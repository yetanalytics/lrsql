-- :name vacuum-analyze!
-- :doc Run the `VACUUM ANALYZE` command, in case it was not done automatically yet.
VACUUM ANALYZE;

-- :name void-statement!
-- :command :execute
-- :result :affected
-- :doc Update a statement such that `is_voided` is set to true. Voiding statements themselves cannot be voided.
UPDATE xapi_statement
SET is_voided = TRUE
WHERE statement_id = :statement-id
AND verb_hash != UNHEX(SHA2('http://adlnet.gov/expapi/verbs/voided', 256));
-- ^ Any Statement that voids another cannot itself be voided.

-- :name update-actor!
-- :command :execute
-- :result :affected
-- :doc Update the payload of a pre-existing actor.
UPDATE actor
SET payload = :payload
WHERE actor_hash = UNHEX(SHA2(:actor-ifi,256))
AND actor_type = :actor-type;

-- :name update-activity!
-- :command :execute
-- :result :affected
-- :doc Update the payload of a pre-existing activity.
UPDATE activity
SET payload = :payload
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256));

-- :name update-state-document!
-- :command :insert
-- :result :affected
-- :doc Update the `contents`, `content_length`, and `last_modified` fields of a state document.
UPDATE state_document
SET
  content_length = :content-length,
  contents = :contents,
  last_modified = :last-modified
WHERE state_hash = UNHEX(SHA2(:state-id,256))
AND state_id = :state-id
AND activity_hash = UNHEX(SHA2(:activity-iri,256))
AND agent_hash = UNHEX(SHA2(:agent-ifi,256))
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name update-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the `contents`, `content_length`, and `last_modified` fields of an agent profile document.
UPDATE agent_profile_document
SET
  content_length = :content-length,
  contents = :contents,
  last_modified = :last-modified
WHERE profile_id = :profile-id
AND agent_hash = UNHEX(SHA2(:agent-ifi,256));

-- :name update-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the `contents`, `content_length`, and `last_modified` fields of an activity profile document
UPDATE activity_profile_document
SET
  content_length = :content-length,
  contents = :contents,
  last_modified = :last-modified
WHERE profile_hash = UNHEX(SHA2(:profile-id,256))
AND profile_id = :profile-id
AND activity_hash = UNHEX(SHA2(:activity-iri,256));

/* Admin Accounts + Credentials */

-- :name update-admin-password!
-- :command :execute
-- :result :affected
-- :doc Update the `passhash` of an admin account.
UPDATE admin_account
SET
  passhash = :new-passhash
WHERE id = :account-id;

-- :name update-credential-label!
-- :command :execute
-- :result :affected
-- :doc Set the `label` column for the corresponding credential.
UPDATE lrs_credential
SET
  label = :label
WHERE api_key = :api-key
AND secret_key = :secret-key;

-- :name update-credential-is-seed!
-- :command :execute
-- :result :affected
-- :doc Set the `is_seed` column for the corresponding credential.
UPDATE lrs_credential
SET
  is_seed = :seed?
WHERE api_key = :api-key
AND secret_key = :secret-key;

-- :name update-one-time-jwt!
-- :command :execute
-- :result :affected
-- :doc Update `blocked_jwt.one_time_id` to be null, thus blocking the JWT.
UPDATE blocked_jwt
SET
  one_time_id = NULL
WHERE one_time_id = :one-time-id;

/* Reactions */

-- :name update-reaction!
-- :command :execute
-- :result :affected
-- :doc Update the `title`, `ruleset` and/or `active` status of a reaction.
UPDATE reaction
SET
--~ (when (:title params) "title = :title,")
--~ (when (:ruleset params) "ruleset = :ruleset,")
--~ (when (or (true? (:active params)) (false? (:active params))) "active = :active,")
--~ (when (:ruleset params) "error = null,")
  modified = :modified
WHERE id = :reaction-id

-- :name error-reaction!
-- :command :execute
-- :result :affected
-- :doc Set the `error` column on a reaction and make it inactive.
UPDATE reaction
SET
  error = :error,
  active = false,
  modified = :modified
WHERE id = :reaction-id
