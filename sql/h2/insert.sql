/* Statement + Attachment Insertion */

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
statement_id = :statement-id,
attachment_sha = :attachment-sha,
content_type = :content-type,
content_length = :content-length,
content = :content

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

/* Document Insertion */

-- :name insert-state-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new state document.
INSERT INTO state_document SET
id = :primary-key,
state_id = :state-id,
activity_iri = :activity-iri,
agent_ifi = :agent-ifi,
registration = :?registration,
last_modified = :last-modified,
document = :document

-- :name insert-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new agent profile document.
INSERT INTO agent_profile_document SET
id = :primary-key,
profile_id = :profile-id,
agent_ifi = :agent-ifi,
last_modified = :last-modified,
document = :document

-- :name insert-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity profile document.
INSERT INTO activity_profile_document SET
id = :primary-key,
profile_id = :profile-id,
activity_iri = :activity-iri,
last_modified = :last-modified,
document = :document

-- :name update-state-document!
-- :command :insert
-- :result :affected
-- :doc Update the document and last_modified fields of a state document.
UPDATE state_document
SET document = :document, last_modified = :last-modified
WHERE state_id = :state-id
AND activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (if (:?registration params) "AND registration = :?registration" "AND registration IS NULL")

-- :name update-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the document and last_modified fields of an agent profile document.
UPDATE agent_profile_document
SET document = :document, last_modified = :last-modified
WHERE profile_id = :profile-id
AND agent_ifi = :agent-ifi

-- :name update-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the document and last_modified fields of an activity profile document
UPDATE activity_profile_document
SET document = :document, last_modified = :last-modified
WHERE profile_id = :profile-id
AND activity_iri = :activity-iri
