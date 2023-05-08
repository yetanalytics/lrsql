(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.actor :as a-util]))


;; PostgreSQL
(comment
  (require
   '[lrsql.postgres.record :as rp]
   '[hugsql.core :as hug])

  (def sys (system/system (rp/map->PostgresBackend {}) :test-postgres))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  ;; Sanity check queries
  (jdbc/execute! ds
                 ["SELECT stmt.payload FROM xapi_statement stmt LIMIT 1"])

  (jdbc/execute! ds
                 ["SELECT COUNT(*) FROM xapi_statement"])

  (jdbc/execute! ds
                 ["ALTER TABLE statement_to_actor ALTER COLUMN actor_ifi TYPE text"])

  ;; Real query test
  (do
    (hug/def-sqlvec-fns "lrsql/postgres/sql/query.sql")

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (def q
      (query-statements-sqlvec
       {:actor-ifi (a-util/actor->ifi {"name" "Bob Nelson", "mbox" "mailto:bob@example.org"})
        :ascending? true
        :related-actors? true
        :limit 10})))
  (println (first q))
  (count (jdbc/execute! ds q))

  (jdbc/execute! ds ["SET enable_indexscan = ON;
                      SET enable_seqscan = ON"])
  (jdbc/execute! ds ["SET enable_hashjoin = OFF;"])

  (->> (update q 0 (fn [qstr] (str "EXPLAIN ANALYZE\n" qstr)))
       (jdbc/execute! ds)
       (map (fn [x] (get x (keyword "QUERY PLAN"))))
       (run! println))

  (component/stop sys')
  )

;; SQLite
(comment
  (require
   '[lrsql.sqlite.record :as r]
   '[lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]])

  (def sys (system/system (r/map->SQLiteBackend {}) :test-sqlite))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (lrsp/-store-statements lrs auth-ident [stmt-1] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])

  (println
   (jdbc/execute! ds
                  ["EXPLAIN QUERY PLAN
                    SELECT count(*)
                    FROM xapi_statement"]))
  )
