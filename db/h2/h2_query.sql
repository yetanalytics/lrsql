-- :name query-statement
-- :command :query
-- :result :many
-- :doc Query a statement using statement resource parameters.
SELECT data FROM statement
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
statementID = :statement-id

-- :snip is-voided
isVoided = :voided?

-- :snip verb-iri
verbIRI = :verb-id

-- :snip registration
registration = :registration

-- :timestamp-since
timestamp > :since

-- :timestamp-until
timestamp <= :until

-- :snip statement-to-agent-join
INNER JOIN statement_to_agent
  ON statement.statementID = statement_to_agent.statementID
  :snip:actor-agent-usage

-- :snip actor-agent-usage
AND statement_to_agent.usage = 'Actor'

-- :snip statement-to-activity-join
INNER JOIN statement_to_activity
  ON statement.statementID = statement_to_activity.statementID
  :snip:object-activity-usage

-- :snip object-activity-usage
AND statement_to_activity.usage = 'Object'

-- :snip limit
LIMIT :limit