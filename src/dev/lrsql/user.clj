(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]))

(comment
  (require '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.util :as util]
           '[criterium.core :as crit]
           '[lrsql.ops.query.auth :as qa])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))
  
  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 2"])

  (do
    (doseq [cmd [;; Drop credentials table
                 "DROP TABLE IF EXISTS lrs_credential"
                 ;; Drop document tables
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

    (component/stop sys')))
