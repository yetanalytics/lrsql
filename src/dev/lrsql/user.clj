(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.util :as util]
           '[criterium.core :as crit])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 2"])

  (crit/bench
   (do (lrsp/-get-statements lrs {} {} #{})
       (lrsp/-get-statements lrs {} {:verb "https://w3id.org/xapi/video/verbs/seeked"} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true :since "2000-01-01T01:00:00Z"} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true :until "3000-01-01T01:00:00Z"} #{})))

  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT DISTINCT stmt.id, stmt.payload
                          FROM xapi_statement stmt
                          LEFT JOIN statement_to_statement ON stmt.statement_id = statement_to_statement.ancestor_id
                          LEFT JOIN xapi_statement stmt_desc ON stmt_desc.statement_id = statement_to_statement.descendant_id
                          WHERE stmt.is_voided = TRUE
                          AND ((1) OR (1))
                          ORDER BY stmt.id ASC
                          LIMIT ?
                          "
                         #_(util/time->uuid (util/str->time "2000-01-01T01:00:00Z"))
                         #_(util/time->uuid (util/str->time "3000-01-01T01:00:00Z"))
                         10])
      first
      :PLAN
      print)

  (do
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

    (component/stop sys')))
