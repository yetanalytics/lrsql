-- :name query-statement
-- :command :query
-- :result :many
-- :doc Query a statement using statement resource parameters.
-- :require [clojure.string :as cstr]
SELECT payload FROM xapi_statement
/*~
(when (:agent-ifi params)
  (cstr/join
   "\n"
   ["INNER JOIN statement_to_agent"
    "ON xapi_statement.statement_id = statement_to_agent.statement_id"
    "AND statement_to_agent.agent_ifi = :agent-ifi"
    (when-not (:related-agents? params) "AND statement_to_agent.usage = 'Actor'")]))
~*/
/*~
(when (:activity-iri params)
  (cstr/join
   "\n"
   ["INNER JOIN statement_to_activity"
    "ON xapi_statement.statement_id = statement_to_activity.statement_id"
    "AND statement_to_activity.activity_iri = :activity-iri"
    (when-not (:related-activites? params) "AND statement_to_activity.usage = 'Object'")]))
~*/
/*~
(let [where-conds
      [(when (:statement-id params) "xapi_statement.statement_id = :statement-id")
       (when (:voided? params) "xapi_statement.is_voided = :voided?")
       (when (:verb-iri params) "xapi_statement.verb_iri = :verb-iri")
       (when (:registration params) "xapi_statement.registration = :registration")
       (when (:since params) "xapi_statement.stored > :since")
       (when (:until params) "xapi_statement.stored <= :until")]
      where-conds'
      (filter some? where-conds)]
 (when (not-empty where-conds')
   (->> where-conds' (cstr/join "\nAND ") (str "WHERE\n"))))
~*/
/*~
(when (:limit params) "LIMIT :limit")
~*/

/* Existence Checks */

-- :name query-agent-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Agent with a given IFI in the agent table. Returns nil iff not found.
SELECT 1 FROM agent
WHERE agent_ifi = :agent-ifi

-- :name query-activity-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Activity with a given IRI in the activity table. Returns nil iff not found.
SELECT 1 FROM activity
WHERE activity_iri = :activity-iri

-- :name query-attachment-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Attachment with a given SHA2 hash in the attachment table. Returns nil iff not found.
SELECT 1 FROM attachment
WHERE attachment_sha = :attachment-sha
