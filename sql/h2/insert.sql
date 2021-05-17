-- :name insert-statement!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement.
INSERT INTO xapi_statement SET
id = :primary-key,
statement_id = :statement-id,
statement_ref_id = :?statement-ref-id,
created = :timestamp,
stored = :stored,
registration = :?registration,
verb_iri = :verb-iri,
is_voided = :voided?,
payload = :payload FORMAT JSON

-- :name void-statement!
-- :command :execute
-- :result :affected
-- :doc Update a statement such that is_voided is set to true.
UPDATE xapi_statement
SET is_voided = TRUE
WHERE statement_id = :statement-id
AND NOT (verb_iri = 'http://adlnet.gov/expapi/verbs/voided')
-- ^ Any Statement that voids another cannot itself be voided.

-- :name insert-agent!
-- :command :insert
-- :result :affected
-- :doc Insert a new agent.
INSERT INTO agent SET 
id = :primary-key,
agent_name = :?name,
agent_ifi = :agent-ifi,
is_identified_group = :identified-group?

-- :name insert-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity.
INSERT INTO activity SET 
id = :primary-key,
activity_iri = :activity-iri,
payload = :payload FORMAT JSON

-- :name insert-attachment!
-- :command :insert
-- :result :affected
-- :doc Insert a new attachment.
INSERT INTO attachment SET 
id = :primary-key,
attachment_sha = :attachment-sha,
content_type = :content-type,
content_length = :content-length,
payload = :payload

-- :name insert-statement-to-agent!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-agent relation.
INSERT INTO statement_to_agent SET 
id = :primary-key,
statement_id = :statement-id,
usage = :usage,
agent_ifi = :agent-ifi

-- :name insert-statement-to-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-activity relation.
INSERT INTO statement_to_activity SET
id = :primary-key,
statement_id = :statement-id,
usage = :usage,
activity_iri = :activity-iri

-- :name insert-statement-to-attachment!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-attachment relation.
INSERT INTO statement_to_attachment SET
id = :primary-key,
statement_id = :statement-id,
attachment_sha = :attachment-sha
