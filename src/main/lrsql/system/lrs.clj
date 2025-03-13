(ns lrsql.system.lrs
  (:require [clojure.set                   :as cset]
            [clojure.tools.logging         :as log]
            [com.stuartsierra.component    :as cmp]
            [next.jdbc                     :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol          :as adp]
            [lrsql.init                    :as init]
            [lrsql.init.oidc               :as oidc-init]
            [lrsql.init.reaction           :as react-init]
            [lrsql.backend.protocol        :as bp]
            [lrsql.input.actor             :as agent-input]
            [lrsql.input.activity          :as activity-input]
            [lrsql.input.admin             :as admin-input]
            [lrsql.input.admin.jwt         :as admin-jwt-input]
            [lrsql.input.admin.status      :as admin-stat-input]
            [lrsql.input.auth              :as auth-input]
            [lrsql.input.statement         :as stmt-input]
            [lrsql.input.document          :as doc-input]
            [lrsql.input.reaction          :as react-input]
            [lrsql.ops.command.admin       :as admin-cmd]
            [lrsql.ops.command.auth        :as auth-cmd]
            [lrsql.ops.command.document    :as doc-cmd]
            [lrsql.ops.command.statement   :as stmt-cmd]
            [lrsql.ops.command.reaction    :as react-cmd]
            [lrsql.ops.query.actor         :as actor-q]
            [lrsql.ops.query.activity      :as activ-q]
            [lrsql.ops.query.admin         :as admin-q]
            [lrsql.ops.query.auth          :as auth-q]
            [lrsql.ops.query.document      :as doc-q]
            [lrsql.ops.query.reaction      :as react-q]
            [lrsql.ops.query.statement     :as stmt-q]
            [lrsql.spec.config             :as cs]
            [lrsql.util.auth               :as auth-util]
            [lrsql.util.oidc               :as oidc-util]
            [lrsql.util.statement          :as stmt-util]
            [lrsql.util                    :as util]
            [lrsql.init.authority          :refer [make-authority-fn]]
            [lrsql.system.util             :refer [assert-config]]
            [lrsql.util.concurrency        :refer [with-rerunable-txn]]))

(defn- lrs-conn
  "Get the connection pool from the LRS instance."
  [lrs]
  (-> lrs
      :connection
      :conn-pool))

(defrecord LearningRecordStore [connection
                                backend
                                config
                                authority-fn
                                oidc-authority-fn
                                reaction-channel]
  cmp/Lifecycle
  (start
    [lrs]
    (assert-config ::cs/lrs "LRS" config)
    (let [;; Destructuring
          {conn :conn-pool}
          connection
          {uname        :admin-user-default
           pass         :admin-pass-default
           api-key      :api-key-default
           srt-key      :api-secret-default
           auth-tp      :authority-template
           oidc-auth-tp :oidc-authority-template}
          config
          ;; Authority function
          auth-fn      (make-authority-fn auth-tp)
          oidc-auth-fn (oidc-init/make-authority-fn oidc-auth-tp)]
      ;; Combine all init ops into a single txn, since the user would expect
      ;; such actions to happen as a single unit. If init-backend! succeeds
      ;; but insert-default-creds! fails, this would constitute a partial
      ;; application of what the user wanted.
      (jdbc/with-transaction [tx conn]
        (init/init-backend! backend tx)
        (init/insert-default-creds! backend tx uname pass api-key srt-key)
        (log/info "Starting new LRS")
        (assoc lrs
               :connection connection
               :authority-fn auth-fn
               :oidc-authority-fn oidc-auth-fn
               :reaction-channel (react-init/reaction-channel config)))))
  (stop
    [lrs]
    (log/info "Stopping LRS...")
    (assoc lrs
           :connection nil
           :authority-fn nil
           :oidc-authority-fn nil
           :reaction-channel nil))

  lrsp/AboutResource
  (-get-about
    [_lrs _auth-identity]
    ;; TODO: Add 2.X.X versions
    {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lrsp/StatementsResource
  (-store-statements
    [lrs auth-identity statements attachments]
    (let [conn
          (lrs-conn lrs)
          authority
          (-> auth-identity :agent)
          stmts
          (map (partial stmt-util/prepare-statement authority)
               statements)
          stmt-inputs
          (-> (map stmt-input/insert-statement-input stmts)
              (stmt-input/add-insert-attachment-inputs
               attachments))
          retry-test   (partial bp/-txn-retry? backend)
          retry-limit  (:stmt-retry-limit config)
          retry-budget (:stmt-retry-budget config)]
      (with-rerunable-txn [tx conn {:retry-test  retry-test
                                    :budget      retry-budget
                                    :max-attempt retry-limit}]
        (loop [stmt-ins stmt-inputs
               stmt-res {:statement-ids []}]
          (if-some [stmt-input (first stmt-ins)]
            ;; Statement input available to insert
            (let [stmt-descs  (stmt-q/query-descendants
                               backend
                               tx
                               stmt-input)
                  stmt-input' (stmt-input/add-insert-descendant-inputs
                               stmt-input
                               stmt-descs)
                  stmt-result (stmt-cmd/insert-statement!
                               backend
                               tx
                               stmt-input')]
              (if-some [err (:error stmt-result)]
                ;; Statement conflict or some other error - stop and rollback
                ;; Return the error, which will either be logged here or
                ;; (if it's unexpected) bubble up until the end
                (do (when (= ::lrsp/statement-conflict (-> err ex-data :type))
                      (log/warn (ex-message err)))
                    (log/warn "Rolling back transaction...")
                    (.rollback tx)
                    stmt-result)
                ;; Non-error result - continue
                (if-some [stmt-id (:statement-id stmt-result)]
                  (do
                    ;; Submit statement for reaction if enabled
                    (react-init/offer-trigger! reaction-channel stmt-id)
                    (recur (rest stmt-ins)
                           (update stmt-res :statement-ids conj stmt-id)))
                  (recur (rest stmt-ins)
                         stmt-res))))
            ;; No more statement inputs - return
            stmt-res)))))

  (-get-statements
    [lrs auth-identity params ltags]
    (let [conn   (lrs-conn lrs)
          config (:config lrs)
          prefix (:stmt-url-prefix config)
          auth?  (-> auth-identity
                     auth-util/most-permissive-statement-read-scope
                     #{:scope/statements.read.mine})
          ?auth  (if auth? (:agent auth-identity) nil)
          inputs (-> params
                     (stmt-util/ensure-default-max-limit config)
                     (stmt-input/query-statement-input ?auth))]
      (jdbc/with-transaction [tx conn]
        (stmt-q/query-statements backend tx inputs ltags prefix))))
  (-consistent-through
    [_lrs _ctx _auth-identity]
    (str (util/current-time)))

  lrsp/DocumentResource
  (-set-document
    [lrs _auth-identity params document merge?]
    (let [conn  (lrs-conn lrs)
          input (doc-input/insert-document-input params document)]
      (jdbc/with-transaction [tx conn]
        (if merge?
          (doc-cmd/upsert-document! backend tx input)
          (doc-cmd/insert-document! backend tx input)))))
  (-get-document
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (doc-input/document-input params)]
      (jdbc/with-transaction [tx conn]
        (doc-q/query-document backend tx input))))
  (-get-document-ids
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (doc-input/document-ids-input params)]
      (jdbc/with-transaction [tx conn]
        (doc-q/query-document-ids backend tx input))))
  (-delete-document
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (doc-input/document-input params)]
      (jdbc/with-transaction [tx conn]
        (doc-cmd/delete-document! backend tx input))))
  (-delete-documents
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (doc-input/document-multi-input params)]
      (jdbc/with-transaction [tx conn]
        (doc-cmd/delete-documents! backend tx input))))

  lrsp/AgentInfoResource
  (-get-person
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (agent-input/query-agent-input params)]
      (jdbc/with-transaction [tx conn]
        (actor-q/query-agent backend tx input))))

  lrsp/ActivityInfoResource
  (-get-activity
    [lrs _auth-identity params]
    (let [conn  (lrs-conn lrs)
          input (activity-input/query-activity-input params)]
      (jdbc/with-transaction [tx conn]
        (activ-q/query-activity backend tx input))))

  lrsp/LRSAuth
  (-authenticate
    [lrs ctx]
    (or
     ;; Token Authentication
     (let [{:keys [oidc-scope-prefix]} config]
       (oidc-util/token-auth-identity
        ctx
        oidc-authority-fn
        oidc-scope-prefix))
     ;; Basic Authentication
     (let [conn   (lrs-conn lrs)
           header (get-in ctx [:request :headers "authorization"])]
       (if-some [key-pair (auth-util/header->key-pair header)]
         (let [{:keys [authority-url]} config
               input (auth-input/query-credential-scopes-input
                      authority-fn
                      authority-url
                      key-pair)]
           (jdbc/with-transaction [tx conn]
             (auth-q/query-credential-scopes backend tx input)))
         ;; No authorization header = no entry
         {:result :com.yetanalytics.lrs.auth/unauthorized}))))
  (-authorize
    [_lrs ctx auth-identity]
    ;; We need to wrap the boolean in a map or else the LRS lib will get
    ;; angry. This isn't documented, but tests will fail w/o the wrapping.
    {:result (auth-util/authorized-action? ctx auth-identity)})

  adp/AdminAccountManager
  (-create-account
    [this username password]
    (let [conn  (lrs-conn this)
          input (admin-input/insert-admin-input username password)]
      (jdbc/with-transaction [tx conn]
        (admin-cmd/insert-admin! backend tx input))))
  (-get-accounts
    [this]
    (let [conn (lrs-conn this)]
      (jdbc/with-transaction [tx conn]
        (admin-q/query-all-admin-accounts backend tx))))
  (-get-account
    [this account-id]
    (let [conn (lrs-conn this)
          input (admin-input/query-account-input account-id)]
      (jdbc/with-transaction [tx conn]
        (select-keys (admin-q/query-admin backend tx input) [:account-id :username]))))
  (-authenticate-account
    [this username password]
    (let [conn  (lrs-conn this)
          input (admin-input/query-validate-admin-input username password)]
      (jdbc/with-transaction [tx conn]
        (admin-q/query-validate-admin backend tx input))))
  (-existing-account?
    [this account-id]
    (let [conn (lrs-conn this)
          input (admin-input/query-admin-exists-input account-id)]
      (jdbc/with-transaction [tx conn]
        (admin-q/query-admin-exists backend tx input))))
  (-delete-account
    [this account-id oidc-enabled?]
    (let [conn  (lrs-conn this)
          input (admin-input/delete-admin-input account-id oidc-enabled?)]
      (jdbc/with-transaction [tx conn]
        (admin-cmd/delete-admin! backend tx input))))
  (-ensure-account-oidc
    [this username oidc-issuer]
    (let [conn  (lrs-conn this)
          input (admin-input/ensure-admin-oidc-input username oidc-issuer)]
      (jdbc/with-transaction [tx conn]
        (admin-cmd/ensure-admin-oidc! backend tx input))))
  (-update-admin-password
    [this account-id old-password new-password]
    (let [conn       (lrs-conn this)
          auth-input (admin-input/query-validate-admin-by-id-input
                      account-id old-password)]
      (jdbc/with-transaction [tx conn]
        (let [{:keys [result]} (admin-q/query-validate-admin
                                backend tx auth-input)]
          (if (uuid? result)
            (let [input (admin-input/update-admin-password-input
                         account-id new-password)]
              (admin-cmd/update-admin-password! backend tx input))
            {:result result})))))
  
  adp/AdminJWTManager
  (-purge-blocklist
   [this leeway]
   (let [conn  (lrs-conn this)
         input (admin-jwt-input/purge-blocklist-input leeway)]
     (jdbc/with-transaction [tx conn]
       (admin-cmd/purge-blocklist! backend tx input))))
  (-block-jwt
   [this jwt exp]
   (let [conn      (lrs-conn this)
         jwt-input (admin-jwt-input/insert-blocked-jwt-input jwt exp)]
     (jdbc/with-transaction [tx conn]
       (admin-cmd/insert-blocked-jwt! backend tx jwt-input))))
  (-jwt-blocked?
   [this jwt]
   (let [conn      (lrs-conn this)
         jwt-input (admin-jwt-input/query-blocked-jwt-input jwt)]
     (jdbc/with-transaction [tx conn]
       (admin-q/query-blocked-jwt-exists backend tx jwt-input))))

  adp/APIKeyManager
  (-create-api-keys
    [this account-id label scopes]
    (let [conn     (lrs-conn this)
          key-pair (auth-util/generate-key-pair)
          cred-in  (auth-input/insert-credential-input
                    account-id
                    key-pair
                    label)
          scope-in (auth-input/insert-credential-scopes-input
                    key-pair
                    scopes)]
      (jdbc/with-transaction [tx conn]
        (auth-cmd/insert-credential! backend tx cred-in)
        (auth-cmd/insert-credential-scopes! backend tx scope-in)
        (assoc key-pair :scopes scopes))))
  (-get-api-keys
    [this account-id]
    (let [conn  (lrs-conn this)
          input (auth-input/query-credentials-input account-id)]
      (jdbc/with-transaction [tx conn]
        (auth-q/query-credentials backend tx input))))
  (-update-api-keys
    ;; TODO: Verify the key pair is associated with the account ID
    [this _account-id api-key secret-key label scopes]
    (let [conn  (lrs-conn this)
          input (auth-input/query-credential-scopes*-input api-key secret-key)]
      (jdbc/with-transaction [tx conn]
        (let [scopes'    (set (:scopes (auth-q/query-credential-scopes*
                                        backend
                                        tx
                                        input)))
              add-scopes (cset/difference scopes scopes')
              del-scopes (cset/difference scopes' scopes)
              add-inputs (auth-input/insert-credential-scopes-input
                          api-key
                          secret-key
                          add-scopes)
              del-inputs (auth-input/delete-credential-scopes-input
                          api-key
                          secret-key
                          del-scopes)
              lab-inputs (auth-input/update-credential-label-input
                          api-key
                          secret-key
                          label)]
          (auth-cmd/update-credential-label! backend tx lab-inputs)
          (auth-cmd/insert-credential-scopes! backend tx add-inputs)
          (auth-cmd/delete-credential-scopes! backend tx del-inputs)
          {:api-key    api-key
           :secret-key secret-key
           :label      label
           :scopes     scopes}))))
  (-delete-api-keys
    [this account-id api-key secret-key]
    (let [conn     (lrs-conn this)
          cred-in  (auth-input/delete-credentials-input
                    account-id
                    api-key
                    secret-key)]
      (jdbc/with-transaction [tx conn]
        (auth-cmd/delete-credential! backend tx cred-in)
        {:api-key    api-key
         :secret-key secret-key})))

  adp/AdminStatusProvider
  (-get-status
    [this params]
    (let [conn  (lrs-conn this)
          input (admin-stat-input/query-status-input params)]
      (jdbc/with-transaction [tx conn]
        (admin-q/query-status backend tx input))))

  adp/AdminReactionManager
  (-create-reaction [this title ruleset active]
    (let [conn  (lrs-conn this)
          input (react-input/insert-reaction-input title ruleset active)]
      (jdbc/with-transaction [tx conn]
        (react-cmd/insert-reaction! backend tx input))))
  (-get-all-reactions [this]
    (let [conn (lrs-conn this)]
      (jdbc/with-transaction [tx conn]
        (react-q/query-all-reactions backend tx))))
  (-update-reaction [this reaction-id title ruleset active]
    (let [conn  (lrs-conn this)
          input (react-input/update-reaction-input
                 reaction-id title ruleset active)]
      (jdbc/with-transaction [tx conn]
        (react-cmd/update-reaction! backend tx input))))
  (-delete-reaction [this reaction-id]
    (let [conn  (lrs-conn this)
          input (react-input/delete-reaction-input reaction-id)]
      (jdbc/with-transaction [tx conn]
        (react-cmd/delete-reaction! backend tx input))))

  adp/AdminLRSManager
  (-delete-actor [this {:keys [actor-ifi]}]
    (let [conn (lrs-conn this)
          input (agent-input/delete-actor-input actor-ifi)]
      (jdbc/with-transaction [tx conn]
        (stmt-cmd/delete-actor! backend tx input)))))
