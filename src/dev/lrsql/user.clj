(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.actor :as a-util]))

;; H2
(comment
  (require
   '[lrsql.h2.record :as r]
   '[lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]])

  (def sys (system/system (r/map->H2Backend {}) :test-h2-mem))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (lrsp/-store-statements lrs auth-ident [stmt-1] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])
  
  (println
   (jdbc/execute! ds
                  ["EXPLAIN ANALYZE
                    SELECT COUNT(*)
                    FROM xapi_statement stmt"]))
  
  (println
   (jdbc/execute! ds
                  ["EXPLAIN ANALYZE
                    SELECT stmt.payload
                    FROM xapi_statement stmt
                    WHERE stmt.statement_id = ?
                    "
                   (get stmt-2 "id")]))
  
  (println
   (jdbc/execute! ds
                  ["EXPLAIN ANALYZE
                    SELECT stmt.payload
                    FROM xapi_statement stmt
                    WHERE stmt.verb_iri = ?
                    AND (
                    SELECT (CASE WHEN COUNT(DISTINCT stmt_actors.actor_ifi) = 2 THEN 1 ELSE 0 END)
                    FROM statement_to_actor stmt_actors
                    WHERE stmt_actors.statement_id = stmt.statement_id
                    AND stmt_actors.actor_ifi IN (?, ?) 
                    AND stmt_actors.usage = 'Authority'
                    )
                    "
                   (get-in stmt-2 ["verb" "id"])
                   (first (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   (second (a-util/actor->ifi-coll (:agent auth-ident-oauth)))]))

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

    (component/stop sys')))

;; PostgreSQL
(comment
  (require
   '[lrsql.postgres.record :as rp]
   '[hugsql.core :as hug])

  (hug/def-sqlvec-fns "lrsql/postgres/sql/query.sql")

  (def sys (system/system (rp/map->PostgresBackend {}) :test-postgres))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (jdbc/execute! ds
                 ["SELECT stmt.payload FROM xapi_statement stmt LIMIT 1"])
  
  (jdbc/execute! ds
                 ["SELECT COUNT(*) FROM xapi_statement"])
  
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (def q
    (query-statements-sqlvec
     {
      :authority-ifis
      (a-util/actor->ifi-coll
       {"account" {"homePage" "http://example.org"
                   "name" "0182eadf-808e-870b-9479-2b66719c11d8"}})
      :authority-ifi-count 1
      ; :actor-ifi (a-util/actor->ifi {"name" "Alice Edwards", "mbox" "mailto:alice@example.org"})
      :actor-ifi (a-util/actor->ifi {"name" "Bob Nelson", "mbox" "mailto:bob@example.org"})
      :ascending? false
      :limit 50}))
  
  (println (first q))

  (jdbc/execute! ds ["SET enable_indexscan = ON;
                      SET enable_seqscan = OFF"])
  (count (jdbc/execute! ds q))
  (->> (update q 0 (fn [qstr] (str "EXPLAIN ANALYZE\n" qstr)))
       (jdbc/execute! ds)
       last
       ; (run! println)
       )

  (component/stop sys')
  )
