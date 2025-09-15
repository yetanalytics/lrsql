(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.actor :as a-util]
            [hugsql.core :as hug]
            [lrsql.lrs-test :refer [stmt-0 stmt-1 stmt-2 auth-ident auth-ident-oauth]]
            [lrsql.test-support :as ts]
            ))


;; SQLite
(comment
  (require
   '[lrsql.sqlite.record :as r])

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
       FROM pragma_table_info('admin_account')
       WHERE name = 'passhash'
       AND \"notnull\" = 1"])

    (jdbc/execute!
     ds
     ["SELECT 1
       FROM pragma_foreign_key_list('statement_to_actor')
       WHERE \"table\" = 'xapi_statement'
       AND on_delete = 'CASCADE'
      "])

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
)

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
          (adp/-create-api-keys lrs "label" account-id ["all"])
          (adp/-create-api-keys lrs "label" account-id ["all/read"])
          (adp/-create-api-keys lrs "label" account-id ["statements/read"])
          (adp/-create-api-keys lrs "label" account-id ["statements/read/mine"])
          (adp/-create-api-keys lrs "label" account-id ["statements/write"])
          (adp/-create-api-keys lrs "label" account-id ["define"])
          (adp/-create-api-keys lrs "label" account-id ["state"])
          (adp/-create-api-keys lrs "label" account-id ["state/read"])
          (adp/-create-api-keys lrs "label" account-id ["activities_profile"])
          (adp/-create-api-keys lrs "label" account-id ["activities_profile/read"])
          (adp/-create-api-keys lrs "label" account-id ["agents_profile"])
          (adp/-create-api-keys lrs "label" account-id ["agents_profile/read"]))
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

;; MariaDB
(comment
  (require
   '[lrsql.maria.record :as rm]
   '[lrsql.init.log]

   '[clj-test-containers.core :as tc]
   '[lrsql.init.config :as cfg]
   )

  ;with running Docker instance

  (def sys (system/system (rm/map->MariaBackend {}) :test-mariadb))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def bk (:backend lrs))

  (def ds (-> sys' :lrs :connection :conn-pool))

  (lrsp/-store-statements lrs auth-ident [stmt-0 stmt-1 stmt-2] [])
  (lrsp/-store-statements lrs auth-ident [stmt-2] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])

  ;; Stop system

  (component/stop sys')


;; Maria with containers
  ;; start a container
  (def container (tc/start! ts/mariadb-container))

  ;; Make a system pointing at it
  (let [{{{:keys [db-port]}
          :database}
         :connection
         :as _raw-cfg} (cfg/read-config :test-mariadb)
        mapped-port   (get (:mapped-ports container) db-port)
        mapped-host   (get container :host)]
    (def sys
      (system/system (rm/map->MariaBackend {}) :test-mariadb
                     :conf-overrides
                     {[:connection :database :db-port] mapped-port
                      [:connection :database :db-host] mapped-host})))

  (do
    (def sys' (component/start sys))
    (def lrs (:lrs sys'))
    (def bk (:backend lrs))
    (def ds (-> sys' :lrs :connection :conn-pool)))

  ;; Sanity check queries
  (jdbc/execute! ds
                 ["SELECT stmt.payload FROM xapi_statement stmt LIMIT 1"])

  (jdbc/execute! ds
                 ["SELECT COUNT(*) FROM xapi_statement"])



  ;; Stop system
  (component/stop sys')

  ;; Stop the container
  (tc/stop! container)


;; Use containers in individual tests from the REPL!


;; Setting one of these fixture modes will result in automatically
;; bootstrapping fixtures so you can use things like cider-test-run-test
(ts/set-db-fixture-mode! :postgres)

(ts/set-db-fixture-mode! :mariadb)
)
