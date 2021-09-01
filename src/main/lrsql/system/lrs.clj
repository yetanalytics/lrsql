(ns lrsql.system.lrs
  (:require [clojure.set :as cset]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as cmp]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.init :as init]
            [lrsql.input.actor     :as agent-input]
            [lrsql.input.activity  :as activity-input]
            [lrsql.input.admin     :as admin-input]
            [lrsql.input.auth      :as auth-input]
            [lrsql.input.statement :as stmt-input]
            [lrsql.input.document  :as doc-input]
            [lrsql.ops.command.admin     :as admin-cmd]
            [lrsql.ops.command.auth      :as auth-cmd]
            [lrsql.ops.command.document  :as doc-cmd]
            [lrsql.ops.command.statement :as stmt-cmd]
            [lrsql.ops.query.actor     :as actor-q]
            [lrsql.ops.query.activity  :as activ-q]
            [lrsql.ops.query.admin     :as admin-q]
            [lrsql.ops.query.auth      :as auth-q]
            [lrsql.ops.query.document  :as doc-q]
            [lrsql.ops.query.statement :as stmt-q]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]]
            [lrsql.util.auth      :as auth-util]
            [lrsql.util.statement :as stmt-util]
            [lrsql.init.authority :refer [make-authority-fn]])
  (:import [java.time Instant]))

(defn- lrs-conn
  "Get the connection pool from the LRS instance."
  [lrs]
  (-> lrs
      :connection
      :conn-pool))

(defrecord LearningRecordStore [connection backend config authority-fn]
  cmp/Lifecycle
  (start
    [lrs]
    (assert-config ::cs/lrs "LRS" config)
    (let [conn    (-> connection :conn-pool)
          uname   (-> config :api-key-default)
          pass    (-> config :api-secret-default)
          auth-tp (-> config :authority-template)
          auth-fn (make-authority-fn auth-tp)]
      (init/init-backend! backend conn)
      (init/insert-default-creds! backend conn uname pass)
      (log/info "Starting new LRS")
      (assoc lrs :connection connection :authority-fn auth-fn)))
  (stop
    [lrs]
    (log/info "Stopping LRS...")
    (assoc lrs :connection nil :authority-fn nil))

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
          stmt-inputs-or-conflict
          (loop [stmt-ins  stmt-inputs
                 stmt-ins' (transient [])]
            (if-some [stmt-in (first stmt-ins)]
              (let [conflict (jdbc/with-transaction [tx conn]
                               (stmt-q/query-statement-conflict
                                backend
                                tx
                                stmt-in))]
                (cond
                  ;; Statement not in DB; continue
                  (nil? conflict)
                  (recur (rest stmt-ins) (conj! stmt-ins' stmt-in))
                  ;; Statement in DB but completely equal; ignore
                  (:equal? conflict)
                  (recur (rest stmt-ins) stmt-ins')
                  ;; Statement in DB with same ID but different contents
                  :else conflict))
              (persistent! stmt-ins')))]
      (if (map? stmt-inputs-or-conflict)
        {:error (ex-info "Statement Conflict!"
                         (merge stmt-inputs-or-conflict
                                {:type ::lrsp/statement-conflict}))}
        (let [stmt-inputs
              stmt-inputs-or-conflict
              _
              (log/errorf "Statement Inputs: %s" stmt-inputs)
              stmt-results
              (map (fn [stmt-input]
                     (jdbc/with-transaction [tx conn]
                       (let [stmt-descs
                             (stmt-q/query-descendants
                              backend
                              tx
                              stmt-input)
                             stmt-input'
                             (stmt-input/add-insert-descendant-inputs
                              stmt-input
                              stmt-descs)]
                         (stmt-cmd/insert-statement!
                          backend
                          tx
                          stmt-input'))))
                   stmt-inputs)]
          {:statement-ids (vec (mapcat :statement-ids stmt-results))}))))
  (-get-statements
    [lrs _auth-identity params ltags]
    (let [conn   (lrs-conn lrs)
          config (:config lrs)
          prefix (:stmt-url-prefix config)
          inputs (->> params
                      (stmt-util/ensure-default-max-limit config)
                      stmt-input/query-statement-input)]
      (jdbc/with-transaction [tx conn]
        (stmt-q/query-statements backend tx inputs ltags prefix))))
  (-consistent-through
    [_lrs _ctx _auth-identity]
    ;; TODO: review, this should be OK because of transactions, but we may want
    ;; to use the tx-inst pattern and set it to that
    (.toString (Instant/now)))

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
        {:result :com.yetanalytics.lrs.auth/unauthorized})))
  (-authorize
    [_lrs ctx auth-identity]
    (auth-util/authorize-action ctx auth-identity))

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
    [this account-id]
    (let [conn  (lrs-conn this)
          input (admin-input/delete-admin-input account-id)]
      (jdbc/with-transaction [tx conn]
        (admin-cmd/delete-admin! backend tx input))))

  adp/APIKeyManager
  (-create-api-keys
    [this account-id scopes]
    (let [conn     (lrs-conn this)
          key-pair (auth-util/generate-key-pair)
          cred-in  (auth-input/insert-credential-input
                    account-id
                    key-pair)
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
    [this _account-id api-key secret-key scopes]
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
                          del-scopes)]
          (auth-cmd/insert-credential-scopes! backend tx add-inputs)
          (auth-cmd/delete-credential-scopes! backend tx del-inputs)
          {:api-key    api-key
           :secret-key secret-key
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
         :secret-key secret-key}))))
