(ns lrsql.lrs
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lp]
            [lrsql.hugsql.command :as command]
            [lrsql.hugsql.init :as init]
            [lrsql.hugsql.input :as input]))

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
   (dissoc lrs :conn-pool conn-pool))

  lp/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  lp/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
   (let [conn   (:conn-pool lrs)
         inputs (input/statements->insert-inputs statements)]
     (jdbc/with-transaction [tx (conn)]
       (command/insert-inputs! tx inputs))))
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
   {})
  (-get-document
   [this auth-identity params]
   {:document nil})
  (-get-document-ids
   [this auth-identity params]
   {:document-ids []})
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
