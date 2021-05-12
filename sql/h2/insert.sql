-- :name insert-statement
-- :command :insert
-- :result :affected
-- :doc Insert a new statement.
INSERT INTO xapi_statement
SET id = :primary-key,
SET statement_id = :statement-id,
SET sub_statement_id = :?sub-statement-id,
SET statement_ref_id = :?statement-ref-id,
SET created = :timestamp,
SET stored = :stored,
SET registration = :?registration,
SET verb_iri = :verb-iri,
SET is_voided = :voided?,
SET payload = :payload

-- :name insert-agent
-- :command :insert
-- :result :affected
-- :doc Insert a new agent.
INSERT INTO agent
SET id = :primary-key,
SET agent_name = :name,
SET agent_ifi = :ifi
SET is_identified_group = :identified-group?

-- :name insert-activity
-- :command :insert
-- :result :affected
-- :doc Insert a new activity.
INSERT INTO activity
SET id = :primary-key,
SET activity_iri = :activity-iri,
SET payload = :payload

-- :name insert-attachment,
-- :command :insert,
-- :result :affected
-- :doc Insert a new attachment
INSERT INTO attachment
SET id = :primary-key,
SET attachment_sha = :attachment-sha,
SET content_type = :content-type,
SET file_url = :file-url,
SET payload = :payload

-- :name insert-statement-to-agent
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-agent relation.
INSERT INTO statement_to_agent
SET id = :primary-key,
SET statement_id = :statement-id,
SET usage = :usage,
SET agent_ifi = :agent-ifi

-- :name insert-statement-to-activity
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-activity relation.
INSERT INTO statement_to_activity
SET id = :primary-key,
SET statement_id = :statement-id,
SET usage = :usage,
SET activity_iri = :activity-iri

-- :name insert-statement-to-attachment
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-attachment relation.
INSERT INTO statement_to_attachment
SET id = :primary-key,
SET statement_id = :statement-id,
SET attachment_sha = :attachment-sha
