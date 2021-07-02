(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.util :as util]
           )

  (def sys (system/system))
  (def sys' (component/start sys))

  (into {} sys)

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))


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
  (lrsp/-store-statements lrs
                        {}
                        (concat [stmt-0' stmt-1' stmt-2']
                                (repeatedly 100000 (fn [] stmt-3'))
                                (repeatedly 100000 (fn [] stmt-4')))
                        {})

  (clojure.pprint/pprint (jdbc/execute! ds ["SELECT * from statement_to_actor LIMIT 10"]))



  (print (-> (jdbc/execute! ds ["EXPLAIN ANALYZE SELECT DISTINCT stmt.id, stmt.payload
  FROM xapi_statement AS stmt
  INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
  AND stmt_actor.usage = 'Actor'
  LEFT JOIN statement_to_statement sts ON stmt.statement_id = sts.ancestor_id
  LEFT JOIN xapi_statement stmt_d ON sts.descendant_id = stmt_d.statement_id
  LEFT JOIN statement_to_actor stmt_d_actor ON stmt_d.statement_id = stmt_d_actor.statement_id
  AND stmt_actor.usage = 'Actor'
  WHERE
  (stmt.is_voided = FALSE
   AND stmt_actor.actor_ifi = ?)
  OR
  (stmt_d.is_voided = FALSE
   AND stmt_d_actor.actor_ifi = ?)
  LIMIT 10", "mbox::mailto:sample.0@example.com", "mbox::mailto:sample.0@example.com"])
                             first
                             :PLAN))



  WITH

  (clojure.pprint/pprint (jdbc/execute! ds ["SELECT COUNT(id) FROM xapi_statement"]))

  (time (print (jdbc/execute! ds ["EXPLAIN ANALYZE
  WITH actors AS (SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                         FROM statement_to_actor stmt_actor
                         WHERE stmt_actor.usage = 'Actor'
                         AND stmt_actor.actor_ifi = ?)
  SELECT stmt.id, stmt.payload
  FROM xapi_statement stmt
  LEFT JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
  LEFT JOIN (SELECT sts.ancestor_id AS ancestor_id, 1 AS matching_descendants
                    FROM xapi_statement stmt_d
                    INNER JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statement_id
                    INNER JOIN statement_to_statement sts ON stmt_d.statement_id = sts.descendant_id)
  AS descendants ON stmt.statement_id = descendants.ancestor_id
  WHERE (stmt_actors.actor_ifi IS NOT NULL OR descendants.matching_descendants IS NOT NULL)", "mbox::mailto:sample.1@example.com"])))

  (time (print (jdbc/execute! ds ["
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
  INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id", "mbox::mailto:sample.1@example.com"])))

  (print )


  WITH actors AS (SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                         FROM statement_to_actor stmt_actor
                         WHERE stmt_actor.usage = 'Actor'
                         AND stmt_actor.actor_ifi = ?)
  SELECT stmt.id, stmt.payload
  FROM xapi_statement stmt
  INNER JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
  LEFT JOIN (SELECT 1 AS matching_descendants
                    FROM xapi_statement stmt_d
                    INNER JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statment_id
                    INNER JOIN statement_to_statement sts ON stmt_d.statement_id = sts.descendant_id
                    WHERE sts.ancestor_id = stmt.statement_id)
  UNION

  INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id



  LEFT JOIN statement_to_statement sts ON stmt.statement_id = sts.ancestor_id
  LEFT JOIN xapi_statement stmt_d ON sts.descendant_id = stmt_d.statement_id
  LEFT JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
  LEFT JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statement_id
  WHERE (stmt_actors.statement_id IS NOT NULL)
  LIMIT 10


  (print (jdbc/execute! ds ["EXPLAIN ANALYZE
  WITH actors AS (SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                         FROM statement_to_actor stmt_actor
                         WHERE stmt_actor.usage = 'Actor'
                         AND stmt_actor.actor_ifi = ?)
  SELECT stmt.id, stmt.payload, stmt_actors.*
  FROM xapi_statement AS stmt
  LEFT JOIN statement_to_statement sts ON stmt.statement_id = sts.ancestor_id
  LEFT JOIN xapi_statement stmt_d ON sts.descendant_id = stmt_d.statement_id
  LEFT JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id
  LEFT JOIN actors stmt_d_actors ON stmt_d.statement_id = stmt_d_actors.statement_id
  WHERE (stmt_actors.statement_id IS NOT NULL)
  LIMIT 10", "mbox::mailto:sample.0@example.com"]))

  (clojure.pprint/pprint (jdbc/execute! ds ["WITH actors AS (SELECT stmt_actor.actor_ifi, stmt_actor.statement_id
                         FROM statement_to_actor stmt_actor
                         WHERE stmt_actor.usage = 'Actor'
                         AND stmt_actor.actor_ifi = ?)
  SELECT * FROM actors",  "mbox::mailto:sample.0@example.com"]))


  SELECT stmt.id, stmt.payload
  FROM xapi_statement AS stmt
  INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
  LEFT JOIN statement_to_statement sts ON stmt.statement_id = sts.ancestor_id
  LEFT JOIN xapi_statement stmt_d ON sts.descendant_id = stmt_d.statement_id
  LEFT JOIN statement_to_actor stmt_d_actor ON stmt_d.statement_id = stmt_d_actor.statement_id
  WHERE
  (stmt.is_voided = FALSE
   AND stmt_actor.actor_ifi = ?
   AND stmt_actor.usage = 'Actor')
  OR
  (stmt_d.is_voided = FALSE
   AND stmt_d_actor.actor_ifi = ?
   AND stmt_d_actor.usage = 'Actor')
  LIMIT 10


  stmt.is_voided = FALSE



  SELECT DISTINCT id, payload FROM (
                                    SELECT stmt.id, stmt.payload
                                    FROM xapi_statement AS stmt
                                    INNER JOIN statement_to_actor stmt_actor ON stmt.statement_id = stmt_actor.statement_id
                                    AND stmt_actor.actor_ifi = ?
                                    AND stmt_actor.usage = 'Actor'
                                    WHERE stmt.is_voided = FALSE
                                    -- AND stmt.id ...
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






  (lrsp/-store-statements lrs {} [stmt-1' stmt-2' stmt-3' stmt-4] [stmt-4-attach])

  (lrsp/-get-statements lrs {} {} #{})
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

  (component/stop sys'))_
