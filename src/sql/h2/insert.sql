/* Statement + Attachment Insertion */

-- :name insert-statement!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement with statement resource params.
INSERT INTO xapi_statement SET
id = :primary-key,
statement_id = :statement-id,
statement_ref_id = :statement-ref-id,
registration = :registration,
verb_iri = :verb-iri,
is_voided = :voided?,
payload = :payload FORMAT JSON

-- :name insert-actor!
-- :command :insert
-- :result :affected
-- :doc Insert a new actor with an IFI and optional name.
INSERT INTO actor SET 
id = :primary-key,
actor_ifi = :actor-ifi,
actor_type = :actor-type,
payload = :payload FORMAT JSON

-- :name insert-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity with an IRI.
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
contents = :contents

-- :name insert-statement-to-actor!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-actor relation.
INSERT INTO statement_to_actor SET 
id = :primary-key,
statement_id = :statement-id,
usage = :usage,
actor_ifi = :actor-ifi,
actor_type = :actor-type

-- :name insert-statement-to-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-activity relation.
INSERT INTO statement_to_activity SET
id = :primary-key,
statement_id = :statement-id,
usage = :usage,
activity_iri = :activity-iri

-- :name insert-statement-to-statement!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-statement relation, where `:ancestor-id` is a previously-inserted statement.
INSERT INTO statement_to_statement SET
id = :primary-key,
ancestor_id = :ancestor-id,
descendant_id = :descendant-id

/* Document Insertion */

-- :name insert-state-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new state document using resource params.
INSERT INTO state_document SET
id = :primary-key,
state_id = :state-id,
activity_iri = :activity-iri,
agent_ifi = :agent-ifi,
registration = :registration,
last_modified = :last-modified,
content_type = :content-type,
content_length = :content-length,
contents = :contents

-- :name insert-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new agent profile document using resource params.
INSERT INTO agent_profile_document SET
id = :primary-key,
profile_id = :profile-id,
agent_ifi = :agent-ifi,
last_modified = :last-modified,
content_type = :content-type,
content_length = :content-length,
contents = :contents

-- :name insert-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity profile document using resource params.
INSERT INTO activity_profile_document SET
id = :primary-key,
profile_id = :profile-id,
activity_iri = :activity-iri,
last_modified = :last-modified,
content_type = :content-type,
content_length = :content-length,
contents = :contents
