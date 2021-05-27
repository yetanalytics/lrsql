(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[lrsql.lrs-test :refer [stmt-0 stmt-1 stmt-2]]
           '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds ((-> sys' :lrs :conn-pool)))

  (lrsp/-store-statements lrs {} [stmt-0 stmt-1 stmt-2] [])

;; LEFT JOIN xapi_statement stmt_desc
;; ON stmt_desc.statement_id = statement_to_statement.descendant_id

  ;; LEFT JOIN statement_to_activity stmt_desc_activ
;; ON stmt_desc.statement_id = stmt_desc_activ.statement_id

  (jdbc/execute! ds ["SELECT *
                      FROM xapi_statement stmt
                      LEFT JOIN statement_to_statement
                      ON stmt.statement_id = statement_to_statement.ancestor_id
                      "])

  (jdbc/execute! ds ["SELECT * FROM statement_to_statement"])

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
