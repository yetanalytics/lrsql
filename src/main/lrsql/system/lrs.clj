(ns lrsql.system.lrs
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.init :as init]
            [lrsql.input.actor     :as agent-input]
            [lrsql.input.activity  :as activity-input]
            [lrsql.input.auth      :as auth-input]
            [lrsql.input.statement :as stmt-input]
            [lrsql.input.document  :as doc-input]
            [lrsql.ops.command.document  :as doc-cmd]
            [lrsql.ops.command.statement :as stmt-cmd]
            [lrsql.ops.query.actor     :as actor-q]
            [lrsql.ops.query.activity  :as activ-q]
            [lrsql.ops.query.auth      :as auth-q]
            [lrsql.ops.query.document  :as doc-q]
            [lrsql.ops.query.statement :as stmt-q]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]]
            [lrsql.util.auth :as auth-util]
            [lrsql.util.statement :as stmt-util]
            [lrsql.util :as u])
  (:import [java.time Instant]))

(defn- lrs-conn
  "Get the connection pool from the LRS instance."
  [lrs]
  (-> lrs :connection :conn-pool))

(defrecord LearningRecordStore [connection config]
  component/Lifecycle
  (start
   [lrs]
   (let [db-type (-> config :database :db-type)
         conn    (-> connection :conn-pool)
         uname   (-> config :api-key-default)
         pass    (-> config :api-secret-default)]
     (assert-config ::cs/lrs "LRS" config)
     (init/init-hugsql-adapter!)
     (init/init-hugsql-fns! db-type)
     (init/create-tables! conn)
     (init/insert-default-creds! conn uname pass)
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
         (-> (map stmt-input/statement-insert-inputs stmts)
             (stmt-input/add-attachment-insert-inputs
              attachments))]
     (jdbc/with-transaction [tx conn]
       (let [stmt-res
             (map (fn [stmt-input]
                    (let [stmt-descs  (stmt-q/query-descendants
                                       tx
                                       stmt-input)
                          stmt-input' (stmt-input/add-descendant-insert-inputs
                                       stmt-input
                                       stmt-descs)]
                      (stmt-cmd/insert-statement!
                       tx
                       stmt-input')))
                  stmt-inputs)]
         {:statement-ids (->> stmt-res
                              (filter some?)
                              (map u/uuid->str)
                              vec)}))))
  (-get-statements
   [lrs _auth-identity params ltags]
   (let [conn   (lrs-conn lrs)
         config (:config lrs)
         inputs (->> params
                     (stmt-util/add-more-url-prefix config)
                     (stmt-util/ensure-default-max-limit config)
                     stmt-input/statement-query-input)]
     (jdbc/with-transaction [tx conn]
       (stmt-q/query-statements tx inputs ltags))))
  (-consistent-through
   [_lrs _ctx _auth-identity]
    ;; TODO: review, this should be OK because of transactions, but we may want
    ;; to use the tx-inst pattern and set it to that
    (.toString (Instant/now)))

  lrsp/DocumentResource
  (-set-document
   [lrs _auth-identity params document merge?]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-insert-input params document)]
     (jdbc/with-transaction [tx conn]
       (if merge?
         (doc-cmd/update-document! tx input)
         (doc-cmd/insert-document! tx input)))))
  (-get-document
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-q/query-document tx input))))
  (-get-document-ids
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-ids-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-q/query-document-ids tx input))))
  (-delete-document
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-cmd/delete-document! tx input))))
  (-delete-documents
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-multi-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-cmd/delete-documents! tx input))))

  lrsp/AgentInfoResource
  (-get-person
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (agent-input/agent-query-input params)]
     (jdbc/with-transaction [tx conn]
       (actor-q/query-agent tx input))))

  lrsp/ActivityInfoResource
  (-get-activity
   [lrs _auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (activity-input/activity-query-input params)]
     (jdbc/with-transaction [tx conn]
       (activ-q/query-activity tx input))))

  lrsp/LRSAuth
  (-authenticate
    [lrs ctx]
    (let [conn   (lrs-conn lrs)
          header (get-in ctx [:request :headers "authorization"])
          input  (auth-input/auth-input header)]
      (jdbc/with-transaction [tx conn]
        (auth-q/query-authentication tx input))))
  (-authorize
   [_lrs ctx auth-identity]
   (auth-util/authorize-action ctx auth-identity)))
