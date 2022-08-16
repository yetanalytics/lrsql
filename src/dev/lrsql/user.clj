(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]))

(comment
  (require
   '[lrsql.h2.record :as r]
   '[lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]]
   '[lrsql.util.actor :as a-util])

  (def sys (system/system (r/map->H2Backend {}) :test-h2-mem))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (lrsp/-store-statements lrs auth-ident [stmt-1] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])
  
  (println
   (jdbc/execute! ds
                  ["EXPLAIN ANALYZE
                    SELECT stmt.payload
                  FROM xapi_statement stmt
                  WHERE stmt.statement_id = ?
                    "
                   (get stmt-2 "id")
                   ]))
  
  (println
   (jdbc/execute! ds
                  ["EXPLAIN ANALYZE
                    SELECT stmt.payload
                  FROM xapi_statement stmt
                  INNER JOIN statement_to_actor stmt_actors ON stmt.statement_id = stmt_actors.statement_id
                  WHERE stmt.statement_id = ?
                    AND stmt_actors.actor_ifi IN (?, ?)
                    AND stmt_actors.usage = 'Authority'
                    GROUP BY stmt.statement_id
                    HAVING COUNT(stmt_actors.actor_ifi) = COUNT((?, ?))
                    "
                   (get stmt-2 "id")
                   #_(a-util/actor->ifi (:agent auth-ident))
                   (first (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   (second (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   (first (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   (second (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   ]))

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
