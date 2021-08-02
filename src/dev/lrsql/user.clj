(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]))

(comment
  (require
   '[lrsql.postgres.record :as pr]
   '[lrsql.lrs-test :refer [stmt-1' stmt-2' stmt-3']])

  (def sys (system/system (pr/map->PostgresBackend {}) :test-postgres))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))


  #_(lrsp/-get-statements lrs {} {:statementId (get stmt-4 "id")} {})

  (lrsp/-store-statements lrs {} [stmt-1' stmt-2' stmt-3'] [])

  (dotimes [_ 1000]
    (lrsp/-get-statements lrs {} {:verb "https://w3id.org/xapi/video/verbs/seeked"} #{})
    (lrsp/-get-statements lrs {} {:agent {"mbox" "mailto:steve@example.org"}} #{})
    (lrsp/-get-statements lrs {} {:activity "https://books.allogy.com/v1/tenant/8/media/cc489c25-8215-4e2d-977d-8dbee098b521"} #{})
    (lrsp/-get-statements lrs {} {:agent {"mbox" "mailto:steve@example.org"}
                                  :activity "https://books.allogy.com/v1/tenant/8/media/cc489c25-8215-4e2d-977d-8dbee098b521"} #{}))


  (jdbc/execute! ds ["ALTER TABLE statement_to_statement
                      ALTER COLUMN ancestor_id
                      SET STATISTICS 100;"])

  (do
    (jdbc/execute! ds ["DELETE FROM statement_to_statement"])
    (jdbc/execute! ds ["DELETE FROM statement_to_actor WHERE statement_id IN (?::uuid, ?::uuid, ?::uuid)"
                       "00000000-0000-4000-0000-000000000001"
                       "00000000-0000-4000-0000-000000000002"
                       "00000000-0000-4000-0000-000000000003"])
    (jdbc/execute! ds ["DELETE FROM statement_to_activity WHERE statement_id IN (?::uuid, ?::uuid, ?::uuid)"
                       "00000000-0000-4000-0000-000000000001"
                       "00000000-0000-4000-0000-000000000002"
                       "00000000-0000-4000-0000-000000000003"])
    (jdbc/execute! ds ["DELETE FROM xapi_statement WHERE statement_id IN (?::uuid, ?::uuid, ?::uuid)"
                       "00000000-0000-4000-0000-000000000001"
                       "00000000-0000-4000-0000-000000000002"
                       "00000000-0000-4000-0000-000000000003"]))

  (jdbc/execute! ds ["ANALYZE VERBOSE statement_to_statement"])
  (jdbc/execute! ds ["SELECT attname, n_distinct FROM pg_stats WHERE tablename = ?"
                     "statement_to_statement"])

  (jdbc/execute! ds ["EXPLAIN ANALYZE
                      SELECT reltuples, relpages
                      FROM pg_class WHERE relname = ?;"
                     "statement_to_statement"])
  
  (jdbc/execute! ds ["-- EXPLAIN ANALYZE
                      SELECT COUNT(*) FROM statement_to_statement"])

  (jdbc/execute! ds ["VACUUM ANALYZE"])

  (last
   (jdbc/execute! ds ["EXPLAIN ANALYZE
                       WITH stmt_refs AS (
                       SELECT reltuples AS count FROM pg_class
                       WHERE relname = 'statement_to_statement'
                       )
                      SELECT all_stmt.id, all_stmt.payload
                      FROM ((
                        SELECT stmt.id, stmt.payload
                        FROM xapi_statement stmt
                        INNER JOIN statement_to_actor stmt_actor
                          ON stmt_actor.statement_id = stmt.statement_id
                          AND stmt_actor.actor_ifi = ?
                          AND stmt_actor.usage = ?::actor_usage_enum
                        WHERE stmt.is_voided = FALSE
                        ORDER BY stmt.id DESC
                        LIMIT ?
                      ) UNION ALL (
                       CASE stmt_a.id, stmt_a.payload
                         WHEN stmt_refs.count = 0 THEN NULL, NULL
                       ELSE (
                        SELECT stmt_a.id, stmt_a.payload
                        FROM statement_to_statement sts
                        INNER JOIN xapi_statement stmt_d ON stmt_d.statement_id = sts.descendant_id
                        INNER JOIN xapi_statement stmt_a ON stmt_a.statement_id = sts.ancestor_id
                        INNER JOIN statement_to_actor stmt_d_actor
                          ON stmt_d_actor.statement_id = stmt_d.statement_id
                          AND stmt_d_actor.actor_ifi = ?
                          AND stmt_d_actor.usage = ?::actor_usage_enum
                        ORDER BY stmt_a.id DESC
                        LIMIT ?
                      ))) AS all_stmt
                      ORDER BY all_stmt.id DESC
                      LIMIT ?
                      "
                      "mbox::mailto:steve@example.org"
                      "Actor"
                      1

                      "mbox::mailto:steve@example.org"
                      "Actor"
                      1

                      1]))

  (last
   (jdbc/execute! ds ["EXPLAIN ANALYZE
                      SELECT all_stmt.id, all_stmt.payload
                      FROM ((
                        SELECT stmt.id, stmt.payload
                        FROM xapi_statement stmt
                        WHERE stmt.is_voided = FALSE
                        ORDER BY stmt.id DESC
                        LIMIT ?
                      ) UNION ALL (
                        SELECT stmt_a.id, stmt_a.payload
                        FROM statement_to_statement sts
                        INNER JOIN xapi_statement stmt_d ON stmt_d.statement_id = sts.descendant_id
                        INNER JOIN xapi_statement stmt_a ON stmt_a.statement_id = sts.ancestor_id
                        ORDER BY stmt_a.id DESC
                        LIMIT ?
                      )) AS all_stmt
                      ORDER BY all_stmt.id DESC
                      LIMIT ?
                      "
                      1
                      1
                      1]))

  (last
   (jdbc/execute! ds ["EXPLAIN ANALYZE
                        SELECT stmt_a.id, stmt_a.payload
                        FROM statement_to_statement sts
                        INNER JOIN xapi_statement stmt_d ON stmt_d.statement_id = sts.descendant_id
                        INNER JOIN xapi_statement stmt_a ON stmt_a.statement_id = sts.ancestor_id
                        ORDER BY (stmt_a.id) ASC
                        LIMIT ?
                      "
                      1]))

  (jdbc/execute! ds ["SELECT COUNT(*) FROM statement_to_statement"])

  (jdbc/execute! ds ["DROP INDEX stmt_ref_idx;"])
  (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS stmt_ref_idx ON statement_to_statement(ancestor_id, descendant_id);"])

  (do
    (doseq [cmd [;; Drop credentials table
                 "DROP TABLE IF EXISTS credential_to_scope"
                 "DROP TABLE IF EXISTS lrs_credential"
                 "DROP TABLE IF EXISTS admin_account"
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

    (component/stop sys'))
  )
