(ns lrsql.system.lrs
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.hugsql.init :as init]
            [lrsql.hugsql.input.agent     :as agent-input]
            [lrsql.hugsql.input.activity  :as activity-input]
            [lrsql.hugsql.input.statement :as stmt-input]
            [lrsql.hugsql.input.document  :as doc-input]
            [lrsql.hugsql.command.agent     :as agent-command]
            [lrsql.hugsql.command.activity  :as activity-command]
            [lrsql.hugsql.command.statement :as stmt-command]
            [lrsql.hugsql.command.document  :as doc-command]
            [lrsql.hugsql.util.statement :as stmt-util]
            [lrsql.hugsql.util :as u])
  (:import [java.time Instant]))

(defrecord LearningRecordStore [db-type conn-pool]
  component/Lifecycle
  (start
   [lrs]
   (init/init-hugsql-adapter!)
   (init/init-hugsql-fns! db-type)
   (init/create-tables! (conn-pool))
   (log/infof "Starting new %s-based LRS" db-type)
   (assoc lrs :conn-pool conn-pool))
  (stop
   [lrs]
   (log/info "Stopping LRS...")
   (assoc lrs :conn-pool nil))

  lrsp/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lrsp/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
   (jdbc/with-transaction [tx ((:conn-pool lrs))]
     (let [stmts       (map stmt-util/prepare-statement statements)
           stmt-inputs (-> (map stmt-input/statement-insert-inputs stmts)
                           (stmt-input/add-attachment-insert-inputs
                            attachments))
           stmt-res    (map (fn [stmt-input]
                              (let [stmt-descs
                                    (stmt-command/query-descendants
                                     tx
                                     stmt-input)
                                    stmt-input'
                                    (stmt-input/add-descendant-insert-inputs
                                     stmt-input
                                     stmt-descs)]
                                (stmt-command/insert-statement!
                                 tx
                                 stmt-input')))
                            stmt-inputs)]
       {:statement-ids (->> stmt-res (map u/uuid->str) vec)})))
  (-get-statements
   [lrs auth-identity params ltags]
   (let [conn   (:conn-pool lrs)
         inputs (stmt-input/statement-query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (stmt-command/query-statements tx inputs ltags))))
  (-consistent-through
   [this ctx auth-identity]
    ;; TODO: review, this should be OK because of transactions, but we may want
    ;; to use the tx-inst pattern and set it to that
    (.toString (Instant/now)))

  lrsp/DocumentResource
  (-set-document
   [lrs auth-identity params document merge?]
   (let [conn  (:conn-pool lrs)
         input (doc-input/document-insert-input params document)]
     (jdbc/with-transaction [tx (conn)]
       (if merge?
         (doc-command/update-document! tx input)
         (doc-command/insert-document! tx input)))))
  (-get-document
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx (conn)]
       (doc-command/query-document tx input))))
  (-get-document-ids
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (doc-input/document-ids-input params)]
     (jdbc/with-transaction [tx (conn)]
       (doc-command/query-document-ids tx input))))
  (-delete-document
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (doc-input/document-input params)]
     (jdbc/with-transaction [tx (conn)]
       (doc-command/delete-document! tx input))))
  (-delete-documents
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (doc-input/document-multi-input params)]
     (jdbc/with-transaction [tx (conn)]
       (doc-command/delete-documents! tx input))))

  lrsp/AgentInfoResource
  (-get-person
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (agent-input/agent-query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (agent-command/query-agent tx input))))

  lrsp/ActivityInfoResource
  (-get-activity
   [lrs auth-identity params]
   (let [conn (:conn-pool lrs)
         input (activity-input/activity-query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (activity-command/query-activity tx input))))

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
