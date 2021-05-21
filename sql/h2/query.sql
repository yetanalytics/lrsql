/* Statement Queries */

-- :name query-statements
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
-- :require [clojure.string :as cstr]
SELECT payload FROM xapi_statement
/*~
(when (:agent-ifi params)
 (str "INNER JOIN statement_to_agent"
      "\nON xapi_statement.statement_id = statement_to_agent.statement_id"
      "\nAND statement_to_agent.agent_ifi = :agent-ifi"
      (when-not (:related-agents? params)
       "\nAND statement_to_agent.usage = 'Actor'")))
~*/
/*~
(when (:activity-iri params)
 (str "INNER JOIN statement_to_activity"
      "\nON xapi_statement.statement_id = statement_to_activity.statement_id"
      "\nAND statement_to_activity.activity_iri = :activity-iri"
      (when-not (:related-activities? params)
       "\nAND statement_to_activity.usage = 'Object'")))
~*/
/*~
(some->>
 [(when (:statement-id params)
   "xapi_statement.statement_id = :statement-id")
  (when (some? (:voided? params))
   "xapi_statement.is_voided = :voided?")
  (when (:verb-iri params)
   "xapi_statement.verb_iri = :verb-iri")
  (when (:registration params)
   "xapi_statement.registration = :registration")
  (when (:since params)
   "xapi_statement.stored > :since")
  (when (:until params)
   "xapi_statement.stored <= :until")]
 (filter some?)
 not-empty
 (cstr/join "\nAND ")
 (str "WHERE\n"))
~*/
--~ (when (:ascending? params) "ORDER BY xapi_statement.stored")
--~ (when (:limit params) "LIMIT :limit")

/* Statement Object Queries */

-- :name query-agent
-- :command :query
-- :result :one
-- :doc Query an agent with `:agent-ifi`.
SELECT payload FROM agent
WHERE agent_ifi = :agent-ifi
AND actor_type = 'Agent' -- query only accepts agents, not groups

-- :name query-activity
-- :command :query
-- :result :one
-- :doc Query an activity with `:activity-iri`.
SELECT payload FROM activity
WHERE activity_iri = :activity-iri

/* Statement Object Existence Checks */

-- :name query-agent-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Agent with `:agent-ifi`. Returns nil iff not found.
SELECT 1 FROM agent
WHERE agent_ifi = :agent-ifi

-- :name query-activity-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Activity with `:activity-iri`. Returns nil iff not found.
SELECT 1 FROM activity
WHERE activity_iri = :activity-iri

/* Attachment Queries */

-- :name query-attachments
-- :command :query
-- :result :many
-- :doc Query for one or more attachments that references `:statement-id`.
SELECT attachment_sha, content_type, content_length, content FROM attachment
WHERE statement_id = :statement-id

/* Document Queries */

-- :name query-state-document
-- :command :query
-- :result :one
-- :doc Query for a single state document using resource params. If `:?registration` is missing then `registration` must be NULL.
SELECT document, state_id, last_modified FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
AND state_id = :state-id
--~ (if (:?registration params) "AND registration = :?registration" "AND registration IS NULL")

-- :name query-agent-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single agent profile document using resource params.
SELECT document, profile_id, last_modified FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
AND profile_id = :profile-id

-- :name query-activity-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single activity profile document using resource params.
SELECT document, profile_id, last_modified FROM activity_profile_document
WHERE activity_iri = :activity-iri
AND profile_id = :profile-id

-- :name query-state-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more state document IDs using resource params. If `:?registration` is missing then `registration` must be NULL.
SELECT state_id FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (when (:?registration params) "AND registration = :?registration" "AND registration IS NULL")
--~ (when (:since params) "AND last_modified > :since")

-- :name query-agent-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more agent profile document profile IDs using resource params.
SELECT profile_id FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
--~ (when (:since params) "AND last_modified > :since")

-- :name query-activity-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more activity profile document IDs using resource params.
SELECT profile_id FROM activity_profile_document
WHERE activity_iri = :activity-iri
--~ (when (:since params) "AND last_modified > :since")
