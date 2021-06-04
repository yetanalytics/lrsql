(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.hugsql.util :as util]
           '[lrsql.lrs-test :refer [stmt-1' stmt-2' stmt-3' stmt-4 stmt-4-attach]])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds ((-> sys' :lrs :conn-pool)))

  (lrsp/-store-statements lrs {} [stmt-1' stmt-2' stmt-3' stmt-4] [stmt-4-attach])

  (jdbc/execute! ds ["SELECT * FROM attachment"])

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
