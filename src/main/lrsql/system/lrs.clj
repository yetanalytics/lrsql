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
            [lrsql.util.statement :as stmt-util])
  (:import [java.time Instant]))

(defn- lrs-conn
  "Get the connection pool from the LRS instance."
  [lrs]
  (-> lrs
      :connection
      :conn-pool))

(defrecord LearningRecordStore [connection backend config]
  cmp/Lifecycle
  (start
    [lrs]
    (let [conn  (-> connection :conn-pool)
          uname (-> config :api-key-default)
          pass  (-> config :api-secret-default)]
      (assert-config ::cs/lrs "LRS" config)
      (init/init-backend! backend conn)
      (init/insert-default-creds! backend conn uname pass)
      (log/info "Starting new LRS")
      (assoc lrs :connection connection)))
  (stop
    [lrs]
    (log/info "Stopping LRS...")
    (assoc lrs :connection nil))

  lrsp/AboutResource
  (-get-about
    [_lrs _auth-identity]
   ;; TODO: Add 2.X.X versions
    {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lrsp/StatementsResource
  (-store-statements
    [lrs _auth-identity statements attachments]
    (let [conn
          (lrs-conn lrs)
          stmts
          (map stmt-util/prepare-statement
               statements)
          stmt-inputs
          (-> (map stmt-input/insert-statement-input stmts)
              (stmt-input/add-insert-attachment-inputs
               attachments))]
      (jdbc/with-transaction [tx conn]
        (let [stmt-results
              (map (fn [stmt-input]
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
                        stmt-input')))
                   stmt-inputs)]
          (if-some [ex (some :error stmt-results)]
            {:error ex}
            {:statement-ids (vec (mapcat :statement-ids stmt-results))})))))
  (-get-statements
    [lrs _auth-identity params ltags]
    (let [conn   (lrs-conn lrs)
          config (:config lrs)
          inputs (->> params
                      (stmt-util/add-more-url-prefix config)
                      (stmt-util/ensure-default-max-limit config)
                      stmt-input/query-statement-input)]
      (jdbc/with-transaction [tx conn]
        (stmt-q/query-statements backend tx inputs ltags))))
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
      (if-some [key-pr (auth-util/header->key-pair header)]
        (let [input (auth-input/query-credential-scopes-input key-pr)]
          (jdbc/with-transaction [tx conn]
            (auth-q/query-credential-scopes backend tx input)))
        {:result :com.yetanalytics.lrs.auth/forbidden})))
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
  (-authenticate-account
    [this username password]
    (let [conn  (lrs-conn this)
          input (admin-input/query-validate-admin-input username password)]
      (jdbc/with-transaction [tx conn]
        (admin-q/query-validate-admin backend tx input))))
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
          input (auth-input/query-credential-scopes-input api-key secret-key)]
      (jdbc/with-transaction [tx conn]
        (let [scopes'    (set (auth-q/query-credential-scopes*
                               backend
                               tx
                               input))
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
