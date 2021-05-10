-- :name query-statement
-- :command :query
-- :result :many
-- :doc Query a statement using statement resource parameters.
SELECT payload FROM xapi_statement
WHERE
:snip:statement-id,
:snip:is-voided,
:snip:verb-iri,
:snip:registration,
:snip:timestamp-since,
:snip:timestamp-until,
:snip:statement-to-agent-join,
:snip:statement-to-activity-join,
:snip:limit,

-- :snip statement-id
statement_id = :statement-id

-- :snip is-voided
is_voided = :voided?

-- :snip verb-iri
verb_iri = :verb-iri

-- :snip registration
registration = :registration

-- :timestamp-since
stored > :since

-- :timestamp-until
stored <= :until

-- :snip statement-to-agent-join
INNER JOIN statement_to_agent
  ON statement.statement_id = statement_to_agent.statement_id
  AND :agent-ifi = statement_to_agent.agent_ifi
  :snip:actor-agent-usage

-- :snip actor-agent-usage
AND statement_to_agent.usage = 'Actor'

-- :snip statement-to-activity-join
INNER JOIN statement_to_activity
  ON statement.statement_id = statement_to_activity.statement_id
  AND :activity-iri = statement_to_activity.activity_iri
  :snip:object-activity-usage

-- :snip object-activity-usage
AND statement_to_activity.usage = 'Object'

-- :snip limit
LIMIT :limit