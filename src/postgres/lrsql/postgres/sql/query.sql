/* Single-statement query */

-- :name query-statement
-- :command :query
-- :result :one
-- :doc Query for one statement using statement IDs.
SELECT payload FROM xapi_statement
WHERE statement_id = :statement-id
--~ (when (some? (:voided? params)) "AND is_voided = :voided?")
;

-- :name query-statement-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of a Statement with `:statement-id`. Returns nil iff not found. Includes voided Statements.
SELECT 1 FROM xapi_statement
WHERE statement_id = :statement-id;

/* Multi-statement query */

-- :frag actors-join-frag
INNER JOIN statement_to_actor stmt_actor
ON stmt.statement_id = stmt_actor.statement_id
AND stmt_actor.actor_ifi = :actor-ifi
--~ (when-not (:related-actors? params) "AND stmt_actor.usage = 'Actor'::actor_usage_enum")

-- :frag activs-join-frag
INNER JOIN statement_to_activity stmt_activ
ON stmt.statement_id = stmt_activ.statement_id
AND stmt_activ.activity_iri = :activity-iri
--~ (when-not (:related-activities? params) "AND stmt_activ.usage = 'Object'::activity_usage_enum")

-- :frag stmt-no-ref-subquery-frag
SELECT stmt.id, stmt.payload
FROM xapi_statement stmt
--~ (when (:actor-ifi params)    ":frag:actors-join-frag")
--~ (when (:activity-iri params) ":frag:activs-join-frag")
WHERE stmt.is_voided = FALSE
--~ (when (:from params)         "AND stmt.id >= :from")
--~ (when (:since params)        "AND stmt.id > :since")
--~ (when (:until params)        "AND stmt.id <= :until")
--~ (when (:verb-iri params)     "AND stmt.verb_iri = :verb-iri")
--~ (when (:registration params) "AND stmt.registration = :registration")
--~ (if (:ascending? params) "ORDER BY stmt.id ASC" "ORDER BY stmt.id DESC")
LIMIT :limit

-- :frag stmt-ref-subquery-frag
SELECT stmt_a.id, stmt_a.payload
FROM xapi_statement stmt
--~ (when (:actor-ifi params)    ":frag:actors-join-frag")
--~ (when (:activity-iri params) ":frag:activs-join-frag")
INNER JOIN statement_to_statement sts ON stmt.statement_id = sts.descendant_id
INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id
WHERE TRUE
--~ (when (:from params)         "AND stmt_a.id >= :from")
--~ (when (:since params)        "AND stmt_a.id > :since")
--~ (when (:until params)        "AND stmt_a.id <= :until")
--~ (when (:verb-iri params)     "AND stmt.verb_iri = :verb-iri")
--~ (when (:registration params) "AND stmt.registration = :registration")
--~ (if (:ascending? params) "ORDER BY stmt_a.id ASC" "ORDER BY stmt_a.id DESC")
LIMIT :limit

-- :name query-statements
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
SELECT DISTINCT ON (all_stmt.id)
  all_stmt.id,
  all_stmt.payload
FROM (
  (:frag:stmt-no-ref-subquery-frag)
  UNION ALL
  (:frag:stmt-ref-subquery-frag))
AS all_stmt
--~ (if (:ascending? params) "ORDER BY all_stmt.id ASC" "ORDER BY all_stmt.id DESC")
LIMIT :limit;

-- :name query-statements-no-refs
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters, without considering statement ref filters.
:frag:stmt-no-ref-subquery-frag

/* Statement Object Queries */

-- :name query-actor
-- :command :query
-- :result :one
-- :doc Query an actor with `:actor-ifi` and `:actor-type`.
SELECT payload FROM actor
WHERE actor_ifi = :actor-ifi
AND actor_type = :actor-type::actor_type_enum;

-- :name query-activity
-- :command :query
-- :result :one
-- :doc Query an activity with `:activity-iri`.
SELECT payload FROM activity
WHERE activity_iri = :activity-iri;

/* Statement Reference Queries */

-- :name query-statement-descendants
-- :command :query
-- :result :many
-- :doc Query for the descendants of a referencing `:ancestor-id`.
SELECT descendant_id FROM statement_to_statement
WHERE ancestor_id = :ancestor-id;

-- :name query-statement-ref-count
-- :command query
-- :result :one
-- :doc Query the number of statement-to-statement relationships.
SELECT reltuples AS refs FROM pg_class
WHERE relname = 'statement_to_statement'

/* Attachment Queries */

-- :name query-attachments
-- :command :query
-- :result :many
-- :doc Query for one or more attachments that references `:statement-id`.
SELECT attachment_sha, content_type, content_length, contents FROM attachment
WHERE statement_id = :statement-id;

/* Document Queries */

-- :name query-state-document
-- :command :query
-- :result :one
-- :doc Query for a single state document using resource params. If `:registration` is missing then `registration` must be NULL.
SELECT contents, content_type, content_length, state_id, last_modified
FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
AND state_id = :state-id
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name query-agent-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single agent profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
AND profile_id = :profile-id;

-- :name query-activity-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single activity profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM activity_profile_document
WHERE activity_iri = :activity-iri
AND profile_id = :profile-id;

-- :name query-state-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular state document exists. If `:registration` is missing then `registration` must be NULL.
SELECT 1 FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
AND state_id = :state-id
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name query-agent-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular agent profile document exists.
SELECT 1 FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
AND profile_id = :profile-id;

-- :name query-activity-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular activity profile document exists.
SELECT 1 FROM activity_profile_document
WHERE activity_iri = :activity-iri
AND profile_id = :profile-id;

-- :name query-state-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more state document IDs using resource params. If `:registration` is missing then `registration` must be NULL.
SELECT state_id FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (when (:registration params) "AND registration = :registration" "AND registration IS NULL")
--~ (when (:since params) "AND last_modified > :since")
;

-- :name query-agent-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more agent profile document profile IDs using resource params.
SELECT profile_id FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
--~ (when (:since params) "AND last_modified > :since")
;

-- :name query-activity-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more activity profile document IDs using resource params.
SELECT profile_id FROM activity_profile_document
WHERE activity_iri = :activity-iri
--~ (when (:since params) "AND last_modified > :since")
;

/* Admin Accounts */

-- :name query-account
-- :command :query
-- :result :one
-- :doc Given an account `username`, return the ID and the hashed password, which can be used to verify the account.
SELECT id, passhash FROM admin_account
WHERE username = :username;

-- :name query-account-exists
-- :command :query
-- :result :one
-- :doc Given an account `username`, return whether the account exists in the table.
SELECT 1 FROM admin_account
WHERE username = :username;

/* Credentials */

-- :name query-credentials
-- :command :query
-- :result :many
-- :doc Query all credentials associated with `:account-id`.
SELECT api_key, secret_key FROM lrs_credential
WHERE account_id = :account-id;

-- :name query-credential-exists
-- :command :query
-- :result :one
-- :doc Query whether a credential with the associated `:api-key` and `:secret-key` exists.
SELECT 1 FROM lrs_credential
WHERE api_key = :api-key
AND secret_key = :secret-key;

-- :name query-credential-scopes
-- :command :query
-- :result :many
-- :doc Given an API key and a secret API key, return all authorized scopes (including NULL). Returns an empty coll if the credential is not present.
SELECT scope FROM credential_to_scope
WHERE api_key = :api-key
AND secret_key = :secret-key;
