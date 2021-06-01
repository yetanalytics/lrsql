(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.hugsql.util.statement :as u])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds ((-> sys' :lrs :conn-pool)))
  
  (doseq [cmd [;; Drop document tables
               "DROP TABLE IF EXISTS state_document"
               "DROP TABLE IF EXISTS agent_profile_document"
               "DROP TABLE IF EXISTS activity_profile_document"
               ;; Drop statement tables
               "DROP TABLE IF EXISTS statement_to_statement"
               "DROP TABLE IF EXISTS statement_to_activity"
               "DROP TABLE IF EXISTS statement_to_actor"
               "DROP TABLE IF EXISTS attachment"
               "DROP TABLE IF EXISTS activity"
               "DROP TABLE IF EXISTS actor"
               "DROP TABLE IF EXISTS xapi_statement"]]
    (jdbc/execute! ds [cmd]))

  (component/stop sys'))
