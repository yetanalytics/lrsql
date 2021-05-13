-- :name query-statement
-- :command :query
-- :result :many
-- :doc Query a statement using statement resource parameters.
SELECT payload FROM xapi_statement
WHERE
:snip:statement-id-snip
:snip:is-voided-snip
:snip:verb-iri-snip
:snip:registration-snip
:snip:timestamp-since-snip
:snip:timestamp-until-snip
:snip:statement-to-agent-join-snip
:snip:statement-to-activity-join-snip
:snip:limit-snip

-- :snip statement-id-snip
statement_id = :statement-id

-- :snip is-voided-snip
is_voided = :voided?

-- :snip verb-iri-snip
verb_iri = :verb-iri

-- :snip registration-snip
registration = :registration

-- :snip timestamp-since-snip
stored > :since

-- :snip timestamp-until-snip
stored <= :until

-- :snip statement-to-agent-join-snip
INNER JOIN statement_to_agent
  ON statement_id = statement_to_agent.statement_id
  AND statement_to_agent.agent_ifi = :agent-ifi
  :snip:actor-agent-usage-snip

-- :snip actor-agent-usage-snip
AND statement_to_agent.usage = 'Actor'

-- :snip statement-to-activity-join-snip
INNER JOIN statement_to_activity
  ON statement_id = statement_to_activity.statement_id
  AND statement_to_activity.activity_iri = :activity-iri
  :snip:object-activity-usage-snip

-- :snip object-activity-usage-snip
AND statement_to_activity.usage = 'Object'

-- :snip limit-snip
LIMIT :limit

/* Existence Checks */

-- :name query-agent-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Agent with a given IFI in the agent table. Returns NULL iff not found.
SELECT 1 FROM agent
WHERE agent_ifi = :agent-ifi

-- :name query-activity-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Activity with a given IRI in the activitie table. Returns NULL iff not found.
SELECT 1 FROM activity
WHERE activity_iri = :activity-iri

-- :name query-attachment-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of an Attachment with a given SHA2 hash in the attachment table. Returns NULL iff not found.
SELECT 1 FROM attachment
WHERE attachment_sha = :attachment-sha
