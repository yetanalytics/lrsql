(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.actor :as a-util]))


;; SQLite
(comment
  (require
   '[lrsql.sqlite.record :as r]
   '[lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]])

  (def sys (system/system (r/map->SQLiteBackend {}) :test-sqlite-mem))
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

  (println
   (jdbc/execute! ds
                  ["EXPLAIN QUERY PLAN
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

    (jdbc/execute!
     ds
     ["SELECT 1
       FROM (
         SELECT sql
         FROM sqlite_master
         WHERE type='table' AND name='credential_to_scope'
       ) AS sub_query
       WHERE sub_query.sql GLOB (
      '*(''statements/write'','
    || '*''statements/read'','
    || '*''statements/read/mine'','
    || '*''all/read'','
    || '*''all'','
    || '*''define'','
    || '*''state'','
    || '*''state/read'','
    || '*''activities_profile'','
    || '*''activities_profile/read'','
    || '*''agents_profile'','
    || '*''agents_profile/read'')*'
  )"])

    (component/stop sys')))

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

  ;; Inserting 1.2 million API keys

  (def account-id
    (-> (adp/-get-accounts lrs) first :account-id))

  (run! (fn [idx]
          (when (zero? (mod idx 1000))
            (println (str "On iteration: " idx)))
          (adp/-create-api-keys lrs account-id ["all"])
          (adp/-create-api-keys lrs account-id ["all/read"])
          (adp/-create-api-keys lrs account-id ["statements/read"])
          (adp/-create-api-keys lrs account-id ["statements/read/mine"])
          (adp/-create-api-keys lrs account-id ["statements/write"])
          (adp/-create-api-keys lrs account-id ["define"])
          (adp/-create-api-keys lrs account-id ["state"])
          (adp/-create-api-keys lrs account-id ["state/read"])
          (adp/-create-api-keys lrs account-id ["activities_profile"])
          (adp/-create-api-keys lrs account-id ["activities_profile/read"])
          (adp/-create-api-keys lrs account-id ["agents_profile"])
          (adp/-create-api-keys lrs account-id ["agents_profile/read"]))
        (range 0 100000))
  
  ;; Note: deletes initial username + password API keys
  (jdbc/execute! ds ["DELETE FROM credential_to_scope"])
  
  ;; Query enums

  (jdbc/execute!
   ds
   [
"SELECT 1
 WHERE enum_range(NULL::scope_enum)::TEXT[]
  = ARRAY[
    'statements/write',
    'statements/read',
    'statements/read/mine',
    'all/read',
    'all',
    'state',
    'state/read',
    'define',
    'activities_profile',
    'activities_profile/read',
    'agents_profile',
    'agents_profile/read'
  ];
"])

  ;; Stop system

  (component/stop sys')
  )
