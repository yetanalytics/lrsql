(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.init :as init]))

(comment
  (def sys (system/system :dev-h2-mem))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (.executeUpdate
   (.prepareStatement (.getConnection ds)
                      "
                      CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC);
                      CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri);
                      CREATE INDEX IF NOT EXISTS registration_idex ON xapi_statement(registration)
                      "))


  (def stmt-1
    {"id"     "5c9cbcb0-18c0-46de-bed1-c622c03163a1"
     "actor"  {"mbox"       "mailto:sample.foo@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

  (def stmt-2
    {"id"     "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"
     "actor"  {"account"    {"name"     "Sample Agent 2"
                             "homePage" "https://example.org"}
               "name"       "Sample Agent 2"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
               "display" {"en-US" "Voided"}}
     "object" {"objectType" "StatementRef"
               "id"         "5c9cbcb0-18c0-46de-bed1-c622c03163a1"}})

  (lrsp/-store-statements lrs {} [stmt-1 stmt-2] [])
  (lrsp/-get-statements lrs {} {:statementId "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"} #{})
  (lrsp/-get-statements lrs
                        {}
                        {:agent {"mbox" "mailto:sample.foo@example.com"}
                         :activity "http://www.example.com/tincan/activities/multipart"
                         :limit 1}
                        #{})

  (jdbc/execute! ds ["PRAGMA index_list(xapi_statement)"])

  (jdbc/execute-batch! ds
                       "
                        CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC);
                        CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri);
                        CREATE INDEX IF NOT EXISTS registration_idex ON xapi_statement(registration)
                        "
                       [[] [] []]
                       {})

  (adp/-create-account lrs "DonaldChamberlin123" "iLoveSql")

  (def account-id
    (:result (adp/-authenticate-account lrs "DonaldChamberlin123" "iLoveSql")))

  (adp/-create-api-keys lrs account-id #{"all"})

  (jdbc/execute! ds ["SELECT * FROM admin_account"])
  (jdbc/execute! ds ["SELECT * FROM lrs_credential"])
  (jdbc/execute! ds ["SELECT * FROM credential_to_scope"])
  
  (adp/-delete-account lrs account-id)

  (jdbc/execute! ds ["WITH actors AS (
                      SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                      FROM statement_to_actor stmt_actor
                      WHERE stmt_actor.actor_ifi = ?
                      AND stmt_actor.usage = 'Actor'
                      )
                      WITH activs AS (
                      SELECT stmt_activ.activity_iri, stmt_activ.statement_id
                      FROM statement_to_activity stmt_activ
                      WHERE stmt_activ.activity_iri = ?
                      AND stmt_activ.usage = 'Object'
                      )
                      SELECT id, payload FROM
                      (SELECT stmt.id, stmt.payload
                      FROM xapi_statement stmt
                      INNER JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
                      INNER JOIN activs stmt_activs ON stmt.statement_id = stmt_activs.statement_id
                      WHERE stmt.is_voided = 0
                      UNION SELECT stmt_a.id, stmt_a.payload
                      FROM xapi_statement stmt_d
                      INNER JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statement_id
                      INNER JOIN activs stmt_d_activs ON stmt_d.statement_id = stmt_d_activs.statement_id
                      INNER JOIN statement_to_statement sts ON stmt_d.statement_id = sts.descendant_id
                      INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id
                      WHERE 1
                      )
                      ORDER BY id DESC
                      LIMIT ?"
                     "mbox::mailt:example@gmail.com"
                     "https://example.org"
                     2])

  (jdbc/execute! ds ["
                      WITH actors AS (
                      SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                      FROM statement_to_actor stmt_actor
                      WHERE stmt_actor.actor_ifi = ?)
                      , activs AS (
                      SELECT stmt_activ.activity_iri, stmt_activ.statement_id
                      FROM statement_to_activity stmt_activ
                      WHERE stmt_activ.activity_iri = ?)
                      SELECT stmt.id, stmt.payload
                      FROM xapi_statement stmt
                      INNER JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
                      -- INNER JOIN activs stmt_activs ON stmt.statement_id = stmt_activs.statement_id
                      WHERE stmt.is_voided = 0
                      "
                     "mbox::mailto:sample.foo@example.com"
                     "http://www.example.com/tincan/activities/multipart"])

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
