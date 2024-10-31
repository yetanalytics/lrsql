-- :name delete-state-document!
-- :command :execute
-- :result :affected
-- :doc Delete a single state document using resource params. If `:registration` is missing then documents with NULL registrations are deleted.
DELETE FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
AND state_id = :state-id
--~ (when (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name delete-state-documents!
-- :command :execute
-- :result :affected
-- :doc Delete one or more state documents. Unlike `delete-state-document`, `:state-id` is not a param.
DELETE FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (when (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name delete-agent-profile-document!
-- :command :execute
-- :result :affected
-- :doc Delete a single agent profile document using resource params.
DELETE FROM agent_profile_document
WHERE profile_id = :profile-id
AND agent_ifi = :agent-ifi;

-- :name delete-activity-profile-document!
-- :command :execute
-- :result :affected
-- :doc Delete a single activity profile document using resource params.
DELETE FROM activity_profile_document
WHERE profile_id = :profile-id
AND activity_iri = :activity-iri;

/* Admin Accounts + Credentials */

-- :name delete-admin-account!
-- :command :execute
-- :result :affected
-- :doc Delete the admin account associated with `:account-id`.
DELETE FROM admin_account
WHERE id = :account-id;

-- :name delete-credential!
-- :command :execute
-- :result :affected
-- :doc Delete the credential specified by `:account-id` and a key pair.
DELETE FROM lrs_credential
WHERE account_id = :account-id
AND api_key = :api-key
AND secret_key = :secret-key;

-- :name delete-credential-scope!
-- :command :execute
-- :result :affected
-- :doc Delete the specified credential scope.
DELETE FROM credential_to_scope
WHERE api_key = :api-key
AND secret_key = :secret-key
AND scope = :scope::scope_enum;

-- :name delete-reaction!
-- :command :execute
-- :result :affected
-- :doc (Soft) delete a reaction.
UPDATE reaction
SET
  active = NULL,
  modified = :modified
WHERE
  id = :reaction-id

----------------------begin components of delete-actor-----
-- :name delete-actor-and-dependents!
-- :command :execute
-- :result :affected
DELETE FROM statement_to_statement
WHERE ancestor_id IN (
  SELECT statement_id FROM statement_to_actor
  WHERE actor_ifi = :actor-ifi
)
OR descendant_id IN (
  SELECT statement_id FROM statement_to_actor
  WHERE actor_ifi = :actor-ifi
);

DELETE FROM statement_to_activity WHERE statement_id IN (
  SELECT statement_id FROM statement_to_actor
  WHERE actor_ifi = :actor-ifi
);

DELETE FROM attachment WHERE statement_id IN (
  SELECT statement_id FROM statement_to_actor
  WHERE actor_ifi = :actor-ifi
);

DELETE FROM xapi_statement WHERE statement_id IN (
  SELECT statement_id FROM statement_to_actor
  WHERE actor_ifi = :actor-ifi
);

DELETE FROM agent_profile_document WHERE agent_ifi = :actor-ifi;

DELETE FROM state_document WHERE agent_ifi = :actor-ifi;

DELETE FROM actor WHERE actor_ifi = :actor-ifi;
------------------end delete-actor--------------------

/* JWT Blocklist */

-- :name delete-blocked-jwt-by-account!
-- :command :execute
-- :result :affected
-- :doc Delete all blocked JWTs associated with a given `:account-id`.
DELETE FROM blocked_jwt
WHERE account_id = :account-id;

-- :name :delete-blocked-jwt-by-time!
-- :command :execute
-- :result :affected
-- :doc Delete all blocked JWTs where `:current-time` is past the expiration time.
DELETE FROM blocked_jwt
WHERE expiration <= :current-time;
