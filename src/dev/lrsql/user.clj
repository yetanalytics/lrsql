(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]))

(comment
  (def sys (system/system :dev))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 0"])
  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 2"])

  (def stmt-0'
    {"id"     "00000000-0000-0000-0000-000000000001"
     "actor"  {"mbox"       "mailto:sample.0@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

  (def stmt-1'
    {"id"     "00000000-0000-0000-0000-000000000002"
     "actor"  {"mbox"       "mailto:sample.1@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"id" "http://www.example.com/tincan/activities/multipart-2"}})

  (def stmt-2'
    {"actor"  {"mbox"       "mailto:sample.2@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"objectType" "StatementRef"
               "id" "00000000-0000-0000-0000-000000000001"}})

  (def stmt-3'
    {"actor"  {"mbox"       "mailto:sample.3@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"objectType" "StatementRef"
               "id" "00000000-0000-0000-0000-000000000002"}})

  (def stmt-4'
    {"actor"  {"mbox"       "mailto:sample.4@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"id" "http://www.example.com/tincan/activities/multipart-3"}})

  (def x
    (lrsp/-store-statements lrs
                            {}
                            (concat [stmt-0' stmt-1' stmt-2']
                                    (repeatedly 100000 (fn [] stmt-3'))
                                    (repeatedly 100000 (fn [] stmt-4')))
                            {}))


  (dotimes [_ 30]
    ;; Descending
    (lrsp/-get-statements lrs
                          {}
                          {}
                          {}) `(lrsp/-get-statements lrs
                                                     {}
                                                     {:since "2020-02-10T11:38:40.219768Z"}
                                                     {})
    (lrsp/-get-statements lrs
                          {}
                          {:verb "https://w3id.org/xapi/video/verbs/played"}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:agent {"mbox" "mailto:bob@example.org"}}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:agent {"mbox" "mailto:bob@example.org"}
                           :verb "https://w3id.org/xapi/video/verbs/played"}
                          {})
    ;; Ascending
    (lrsp/-get-statements lrs
                          {}
                          {:ascending true}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:since "2020-02-10T11:38:40.219768Z"
                           :ascending true}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:verb "https://w3id.org/xapi/video/verbs/played"
                           :ascending true}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:agent {"mbox" "mailto:bob@example.org"}
                           :ascending true}
                          {})
    (lrsp/-get-statements lrs
                          {}
                          {:agent {"mbox" "mailto:bob@example.org"}
                           :verb "https://w3id.org/xapi/video/verbs/played"
                           :ascending true}
                          {}))

  (jdbc/execute! ds ["SELECT stmt.id, stmt.payload
                      FROM xapi_statement AS stmt
                      INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
                        AND stmt_actor.actor_ifi = ?
                        AND stmt_actor.usage = 'Actor'
                      WHERE stmt.is_voided = FALSE
                        AND stmt.verb_iri = ?
                      LIMIT ?"
                     "mbox::mailto:bob@example.org"
                     "https://w3id.org/xapi/video/verbs/played"
                     51])

  (jdbc/execute! ds ["SELECT DISTINCT stmt.id, stmt.payload
                      FROM xapi_statement AS stmt
                      INNER JOIN statement_to_statement ON stmt.statement_id = statement_to_statement.ancestor_id
                      INNER JOIN xapi_statement stmt_desc ON stmt_desc.statement_id = statement_to_statement.descendant_id
                      INNER JOIN statement_to_actor stmt_desc_actor ON stmt_desc.statement_id = stmt_desc_actor.statement_id
                        AND stmt_desc_actor.actor_ifi = ?
                        AND stmt_desc_actor.usage = 'Actor'
                      WHERE 1 AND stmt_desc.verb_iri = ?
                      LIMIT ?"
                     "mbox::mailto:bob@example.org"
                     "https://w3id.org/xapi/video/verbs/played"
                     51])

  (jdbc/execute! ds ["
                          
                          SELECT stmt_ans.id, stmt_ans.payload
                          FROM (
                            SELECT stmt.id AS desc_id, stmt.payload
                            FROM xapi_statement AS stmt
                            INNER JOIN statement_to_actor stmt_actor
                              ON stmt.statement_id = stmt_actor.statement_id
                              AND stmt_actor.actor_ifi = ?
                              AND stmt_actor.usage = 'Actor'
                            WHERE stmt.is_voided = FALSE
                              AND stmt.verb_iri = ?
                          ) AS stmt
                           INNER JOIN statement_to_statement sts
                            ON desc_id = sts.descendant_id
                          INNER JOIN xapi_statement stmt_ans
                            ON sts.ancestor_id = stmt_ans.statement_id
                          LIMIT ?"
                     "mbox::mailto:bob@example.org"
                     "https://w3id.org/xapi/video/verbs/played"
                     51
                     51])

  (jdbc/execute! ds ["
                      SELECT stmt_ans.id, stmt_ans.payload
                      FROM (
                        SELECT stmt.statement_id AS desc_id
                        FROM xapi_statement AS stmt
                        INNER JOIN statement_to_actor stmt_actor
                          ON stmt.statement_id = stmt_actor.statement_id
                          AND stmt_actor.actor_ifi = ?
                          AND stmt_actor.usage = 'Actor'
                        WHERE 1
                          AND stmt.verb_iri = ?
                      )
                      INNER JOIN statement_to_statement sts
                        ON desc_id = sts.descendant_id
                      INNER JOIN xapi_statement stmt_ans
                        ON sts.ancestor_id = stmt_ans.statement_id
                      WHERE 1
                        -- AND stmt_ans.id ...
                      ORDER BY stmt_ans.id DESC
                      LIMIT ?
                      "
                     "mbox::mailto:bob@example.org"
                     "https://w3id.org/xapi/video/verbs/played"
                     51])

  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                      WITH stmt_tbl AS (
                        SELECT
                          stmt.id AS stmt_pk,
                          stmt.statement_id AS stmt_id,
                          stmt.payload AS stmt_payload
                        FROM xapi_statement AS stmt
                        INNER JOIN statement_to_actor stmt_actor
                          ON stmt.statement_id = stmt_actor.statement_id
                          AND stmt_actor.actor_ifi = ?
                          AND stmt_actor.usage = 'Actor'
                        WHERE 1
                          AND stmt.verb_iri = ?
                      )
                      SELECT DISTINCT id, payload FROM (
                        SELECT stmt_id AS id, stmt_payload AS payload
                        FROM stmt_tbl
                        LIMIT ?
                      ) UNION ALL (
                        SELECT stmt_ans.id AS id, stmt_ans.payload AS payload
                        FROM stmt_tbl
                        INNER JOIN statement_to_statement sts
                          ON stmt_id = sts.descendant_id
                        INNER JOIN xapi_statement stmt_ans
                          ON sts.ancestor_id = stmt_ans.statement_id
                        LIMIT ?
                      )
                      ORDER BY id DESC
                      LIMIT ?
                      "
                         "mbox::mailto:bob@example.org"
                         "https://w3id.org/xapi/video/verbs/played"
                         51
                         51
                         51])
      first
      :PLAN
      print)

  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                      SELECT DISTINCT id, payload FROM (
                        SELECT stmt.id, stmt.payload
                        FROM xapi_statement AS stmt
                        INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
                          AND stmt_actor.actor_ifi = ?
                          AND stmt_actor.usage = 'Actor'
                        WHERE stmt.is_voided = FALSE
                        LIMIT ?
                      ) UNION ALL (
                        SELECT stmt_ans.id, stmt_ans.payload
                        FROM (
                          SELECT stmt.statement_id AS desc_id
                          FROM xapi_statement AS stmt
                          INNER JOIN statement_to_actor stmt_actor
                            ON stmt.statement_id = stmt_actor.statement_id
                            AND stmt_actor.actor_ifi = ?
                            AND stmt_actor.usage = 'Actor'
                          WHERE 1
                        )
                        INNER JOIN statement_to_statement sts
                          ON desc_id = sts.descendant_id
                        INNER JOIN xapi_statement stmt_ans
                          ON sts.ancestor_id = stmt_ans.statement_id
                        WHERE 1
                          -- AND stmt_ans.id ...
                        LIMIT ?
                      )
                      ORDER BY id DESC
                      LIMIT ?
                      "
                         "mbox::mailto:sample.4@example.com"
                         51
                         "mbox::mailto:sample.4@example.com"
                         51
                         51])
      first
      :PLAN
      print)

  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT DISTINCT stmt_id, stmt_payload
                          FROM (
                            SELECT
                              stmt.id AS stmt_pk,
                              stmt.statement_id AS stmt_id,
                              stmt.payload AS stmt_payload
                            FROM xapi_statement AS stmt
                            INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
                              AND stmt_actor.actor_ifi = ?
                              AND stmt_actor.usage = 'Actor'
                            WHERE stmt.is_voided = FALSE
                            LIMIT ?
                          )
                          LEFT JOIN statement_to_statement sts
                            ON stmt_id = sts.descendant_id
                          LEFT JOIN xapi_statement AS stmt_ans
                            ON sts.ancestor_id = stmt_ans.statement_id
                          WHERE 1
                            -- AND (stmt_ans.id IS NULL AND stmt_id ...) OR (stmt_ans.id ...)
                          LIMIT ?
                          "
                         "mbox::mailto:sample.4@example.com"
                         51
                         51])
      first
      :PLAN
      print)

  (-> (jdbc/execute! ds ["
  WITH actors AS (SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                         FROM statement_to_actor stmt_actor
                         WHERE stmt_actor.usage = 'Actor'
                         AND stmt_actor.actor_ifi = ?)
  SELECT stmt.id, stmt.payload
  FROM xapi_statement stmt
  INNER JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
  UNION
  SELECT stmt_a.id, stmt_a.payload
  FROM xapi_statement stmt_d
  INNER JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statement_id
  INNER JOIN statement_to_statement sts ON stmt_d.statement_id = sts.descendant_id
  INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id
                          "
                         , "mbox::mailto:sample.1@example.com"])
      first
      :PLAN
      print)

  (-> (jdbc/execute! ds ["
                          WITH activs AS (
  SELECT stmt_activ.activity_iri, stmt_activ.statement_id
  FROM statement_to_activity stmt_activ
  WHERE stmt_activ.activity_iri = ?
  AND stmt_activ.usage = 'Object'
)
SELECT id, payload FROM
((SELECT stmt.id, stmt.payload
FROM xapi_statement stmt
INNER JOIN activs stmt_activs ON stmt.statement_id = stmt_activs.statement_id
WHERE stmt.is_voided = FALSE
ORDER BY stmt.id DESC
LIMIT ?
) UNION (SELECT stmt_a.id, stmt_a.payload
FROM xapi_statement stmt_d
INNER JOIN activs stmt_d_activs ON stmt_d.statement_id = stmt_d_activs.statement_id
INNER JOIN statement_to_statement sts ON stmt_d.statement_id = sts.descendant_id
INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id
WHERE 1
ORDER BY stmt_a.id DESC
LIMIT ?
))
ORDER BY id DESC
LIMIT ? [90012-200]
                          "
                         "http://example.org/my-activity-type"
                         51
                         51
                         51])
      first
      :PLAN
      print)
  
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
