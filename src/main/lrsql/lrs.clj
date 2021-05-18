(ns lrsql.lrs
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lp]
            [lrsql.hugsql.command :as command]
            [lrsql.hugsql.init :as init]
            [lrsql.hugsql.input :as input]
            [lrsql.hugsql.util :as u]))

(defrecord LearningRecordStore [db-type conn-pool]
  component/Lifecycle
  (start
   [lrs]
   (init/init-hugsql-adapter!)
   (init/init-hugsql-fns! db-type)
   (init/create-tables! (conn-pool))
   (assoc lrs :conn-pool conn-pool))
  (stop
   [lrs]
   (assoc lrs :conn-pool nil))

  lp/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lp/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
   (let [conn        (:conn-pool lrs)
         stmts       (map u/prepare-statement statements)
         stmt-inputs (input/statements->insert-inputs stmts)
         att-inputs  (when (not-empty attachments)
                       (input/attachments->insert-inputs stmts attachments))]
     (jdbc/with-transaction [tx (conn)]
       (command/insert-inputs! tx (concat stmt-inputs att-inputs)))))
  (-get-statements
   [lrs auth-identity params ltags]
   (let [conn   (:conn-pool lrs)
         inputs (input/params->query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (command/query-statement-input tx inputs))))
  (-consistent-through
   [this ctx auth-identity]
   "timestamp-here") ; TODO: return needs to be a timestamp

  lp/DocumentResource
  (-set-document
   [lrs auth-identity params document merge?]
   (let [conn  (:conn-pool lrs)
         input (input/document->insert-input params document)]
     (jdbc/with-transaction [tx (conn)]
       (command/insert-input! tx input))))
  (-get-document
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (input/params->document-query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (command/query-document tx input))))
  (-get-document-ids
   [lrs auth-identity params]
   (let [conn  (:conn-pool lrs)
         input (input/params->document-query-input params)]
     (jdbc/with-transaction [tx (conn)]
       (command/query-document-ids tx input))))
  (-delete-document
   [this auth-identity params]
   {})
  (-delete-documents
   [this auth-identity params]
   {})

  lp/AgentInfoResource
  (-get-person
   [this auth-identity params]
   {:person {:objectType "Person"}})

  lp/ActivityInfoResource
  (-get-activity
   [this auth-identity params]
   {:activity {:objectType "Activity"}})

  lp/LRSAuth
  (-authenticate
   [this ctx]
   {:result ::forbidden})
  (-authorize
   [this ctx auth-identity]
   {:result false}))
