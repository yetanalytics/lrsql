(ns lrsql.system.lrs
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.init :as init]
            [lrsql.input.actor     :as agent-input]
            [lrsql.input.activity  :as activity-input]
            [lrsql.input.statement :as stmt-input]
            [lrsql.input.document  :as doc-input]
            [lrsql.ops.command.document  :as doc-cmd]
            [lrsql.ops.command.statement :as stmt-cmd]
            [lrsql.ops.query.actor     :as actor-q]
            [lrsql.ops.query.activity  :as activ-q]
            [lrsql.ops.query.document  :as doc-q]
            [lrsql.ops.query.statement :as stmt-q]
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
   (if-not (:connection lrs)
     (do
       (init/init-hugsql-adapter!)
       ;; TODO: calling a dependency's config var seems like a code smell
       (init/init-hugsql-fns! (-> connection :config :db-type))
       (init/create-tables! (:conn-pool connection))
       (log/info "Starting new LRS")
       (assoc lrs :connection connection))
     (do
       (log/info "LRS already started; do nothing.")
       lrs)))
  (stop
   [lrs]
   (if (:connection lrs)
     (do
       (log/info "Stopping LRS...")
       (assoc lrs :connection nil))
     (do
       (log/info "LRS already stopped; do nothing...")
       lrs)))

  lrsp/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lrsp/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
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
   [lrs auth-identity params ltags]
   (let [conn   (lrs-conn lrs)
         inputs (stmt-input/statement-query-input params)]
     (jdbc/with-transaction [tx conn]
       (stmt-q/query-statements tx inputs ltags))))
  (-consistent-through
   [this ctx auth-identity]
    ;; TODO: review, this should be OK because of transactions, but we may want
    ;; to use the tx-inst pattern and set it to that
    (.toString (Instant/now)))

  lrsp/DocumentResource
  (-set-document
   [lrs auth-identity params document merge?]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-insert-input params document)]
     (jdbc/with-transaction [tx conn]
       (if merge?
         (doc-cmd/update-document! tx input)
         (doc-cmd/insert-document! tx input)))))
  (-get-document
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-q/query-document tx input))))
  (-get-document-ids
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-ids-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-q/query-document-ids tx input))))
  (-delete-document
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-cmd/delete-document! tx input))))
  (-delete-documents
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (doc-input/document-multi-input params)]
     (jdbc/with-transaction [tx conn]
       (doc-cmd/delete-documents! tx input))))

  lrsp/AgentInfoResource
  (-get-person
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (agent-input/agent-query-input params)]
     (jdbc/with-transaction [tx conn]
       (actor-q/query-agent tx input))))

  lrsp/ActivityInfoResource
  (-get-activity
   [lrs auth-identity params]
   (let [conn  (lrs-conn lrs)
         input (activity-input/activity-query-input params)]
     (jdbc/with-transaction [tx conn]
       (activ-q/query-activity tx input))))

  lrsp/LRSAuth
  (-authenticate
    [this ctx]
    ;; TODO: Actual auth
    {:result
     {:scopes #{:scope/all}
      :prefix ""
      :auth {:no-op {}}}})
  (-authorize
   [this ctx auth-identity]
   {:result true}))
