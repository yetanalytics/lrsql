/* Statement Queries */

/*
 `id` (the primary key) is a SQUUID with guarenteed monotonicity. This is
 important for the following:
 - As a cursor to the next page of query results when `limit` is applied.
   `id` thus must always be sequential; otherwise, for instance, new
   statements may be inserted mid-page instead of appended.
 - As a secondary sort property for query results; we cannot only apply
   `stored` as the only property since it only has millisecond resolution,
   which is not good enough for deterministic results.
*/

/* Authority subquery fragments */
-- Solution taken from https://stackoverflow.com/a/66315951

-- :frag h2-auth-subquery
AND (
  SELECT COUNT(DISTINCT stmt_auth.actor_ifi) = :authority-ifi-count
     AND EVERY(stmt_auth.actor_ifi IN (:v*:authority-ifis))
  FROM statement_to_actor stmt_auth
  WHERE stmt_auth.statement_id = stmt.statement_id
    AND stmt_auth.usage = 'Authority'
)

-- :frag h2-auth-ref-subquery
AND (
  SELECT COUNT(DISTINCT stmt_auth.actor_ifi) = :authority-ifi-count
     AND EVERY(stmt_auth.actor_ifi IN (:v*:authority-ifis))
  FROM statement_to_actor stmt_auth
  WHERE stmt_auth.statement_id = stmt_desc.statement_id
    AND stmt_auth.usage = 'Authority'
)

/* Single-statement query */

-- :name query-statement
-- :command :query
-- :result :one
-- :doc Query for one statement using statement IDs.
SELECT stmt.payload
FROM xapi_statement stmt
WHERE stmt.statement_id = :statement-id
--~ (when (some? (:voided? params)) "AND stmt.is_voided = :voided?")
--~ (when (:authority-ifis params)  ":frag:h2-auth-subquery")

-- :name query-statement-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of a Statement with `:statement-id`. Returns nil iff not found. Includes voided Statements.
SELECT 1 FROM xapi_statement
WHERE statement_id = :statement-id

/* Multi-statement query */

-- :frag h2-actors-table
actors AS (
  SELECT stmt_actor.statement_id
  FROM statement_to_actor stmt_actor
  WHERE stmt_actor.actor_ifi = :actor-ifi
  --~ (when-not (:related-actors? params) "AND stmt_actor.usage = 'Actor'")
)

-- :frag h2-activities-table
activs AS (
  SELECT stmt_activ.statement_id
  FROM statement_to_activity stmt_activ
  WHERE stmt_activ.activity_iri = :activity-iri
  --~ (when-not (:related-activities? params) "AND stmt_activ.usage = 'Object'")
)

-- :frag h2-stmt-no-ref-subquery-frag
SELECT stmt.id, stmt.payload
FROM xapi_statement stmt
--~ (when (:actor-ifi params)      "INNER JOIN actors stmt_actors ON stmt.statement_id = stmt_actors.statement_id")
--~ (when (:activity-iri params)   "INNER JOIN activs stmt_activs ON stmt.statement_id = stmt_activs.statement_id")
WHERE stmt.is_voided = FALSE
/*~ (when (:from params)
      (if (:ascending? params)     "AND stmt.id >= :from" "AND stmt.id <= :from")) ~*/
--~ (when (:since params)          "AND stmt.id > :since")
--~ (when (:until params)          "AND stmt.id <= :until")
--~ (when (:verb-iri params)       "AND stmt.verb_iri = :verb-iri")
--~ (when (:registration params)   "AND stmt.registration = :registration")
--~ (when (:authority-ifis params) ":frag:h2-auth-subquery")
--~ (if (:ascending? params)       "ORDER BY stmt.id ASC" "ORDER BY stmt.id DESC")
LIMIT :limit

-- :frag h2-stmt-ref-subquery-frag
SELECT stmt.id, stmt.payload
FROM xapi_statement stmt_desc
--~ (when (:actor-ifi params)      "INNER JOIN actors stmt_d_actors ON stmt_desc.statement_id = stmt_d_actors.statement_id")
--~ (when (:activity-iri params)   "INNER JOIN activs stmt_d_activs ON stmt_desc.statement_id = stmt_d_activs.statement_id")
INNER JOIN statement_to_statement sts ON stmt_desc.statement_id = sts.descendant_id
INNER JOIN xapi_statement stmt ON sts.ancestor_id = stmt.statement_id
WHERE stmt.is_voided = FALSE
/*~ (when (:from params)
      (if (:ascending? params)     "AND stmt.id >= :from" "AND stmt.id <= :from"))  ~*/
--~ (when (:since params)          "AND stmt.id > :since")
--~ (when (:until params)          "AND stmt.id <= :until")
--~ (when (:verb-iri params)       "AND stmt_desc.verb_iri = :verb-iri")
--~ (when (:registration params)   "AND stmt_desc.registration = :registration")
--~ (when (:authority-ifis params) ":frag:h2-auth-subquery :frag:h2-auth-ref-subquery")
--~ (if (:ascending? params)       "ORDER BY stmt.id ASC" "ORDER BY stmt.id DESC")
LIMIT :limit

-- :name query-statements
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
/*~
(some->> (cond-> []
           (:actor-ifi params)    (conj ":frag:h2-actors-table")
           (:activity-iri params) (conj ":frag:h2-activities-table"))
         not-empty
         (clojure.string/join ", ")
         (str "WITH "))
~*/
SELECT id, payload FROM
((:frag:h2-stmt-no-ref-subquery-frag) UNION (:frag:h2-stmt-ref-subquery-frag))
--~ (if (:ascending? params) "ORDER BY id ASC" "ORDER BY id DESC")
LIMIT :limit

/* Statement Object Queries */

-- :name query-actor
-- :command :query
-- :result :one
-- :doc Query an actor with `:actor-ifi` and `:actor-type`.
SELECT payload FROM actor
WHERE actor_ifi = :actor-ifi
AND actor_type = :actor-type

-- :name query-activity
-- :command :query
-- :result :one
-- :doc Query an activity with `:activity-iri`.
SELECT payload FROM activity
WHERE activity_iri = :activity-iri

/* Statement Reference Queries */

-- :name query-statement-descendants
-- :command :query
-- :result :many
-- :doc Query for the descendants of a referencing `:ancestor-id`.
SELECT descendant_id FROM statement_to_statement
WHERE ancestor_id = :ancestor-id

/* Attachment Queries */

-- :name query-attachments
-- :command :query
-- :result :many
-- :doc Query for one or more attachments that references `:statement-id`.
SELECT attachment_sha, content_type, content_length, contents FROM attachment
WHERE statement_id = :statement-id

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

-- :name query-agent-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single agent profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
AND profile_id = :profile-id

-- :name query-activity-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single activity profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM activity_profile_document
WHERE activity_iri = :activity-iri
AND profile_id = :profile-id

-- :name query-state-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular state document exists. If `:registration` is missing then `registration` must be NULL.
SELECT 1 FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
AND state_id = :state-id
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")

-- :name query-agent-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular agent profile document exists.
SELECT 1 FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
AND profile_id = :profile-id

-- :name query-activity-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular activity profile document exists.
SELECT 1 FROM activity_profile_document
WHERE activity_iri = :activity-iri
AND profile_id = :profile-id

-- :name query-state-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more state document IDs using resource params. If `:registration` is missing then `registration` must be NULL.
SELECT state_id FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (when (:registration params) "AND registration = :registration" "AND registration IS NULL")
--~ (when (:since params) "AND last_modified > :since")

-- :name query-agent-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more agent profile document profile IDs using resource params.
SELECT profile_id FROM agent_profile_document
WHERE agent_ifi = :agent-ifi
--~ (when (:since params) "AND last_modified > :since")

-- :name query-activity-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more activity profile document IDs using resource params.
SELECT profile_id FROM activity_profile_document
WHERE activity_iri = :activity-iri
--~ (when (:since params) "AND last_modified > :since")

/* Admin Accounts */

-- :name query-account
-- :command :query
-- :result :one
-- :doc Given an account `username`, return the ID and the hashed password, which can be used to verify the account.
SELECT id, passhash FROM admin_account
WHERE username = :username

-- :name query-account-oidc
-- :command :query
-- :result :one
-- :doc Given an account `username`, return the ID and OIDC issuer, which can be used to verify the OIDC identity.
SELECT id, oidc_issuer FROM admin_account
WHERE username = :username

-- :name query-all-accounts
-- :command :query
-- :result :many
-- :doc Return all admin accounts.
SELECT id, username FROM admin_account

-- :name query-account-exists
-- :command :query
-- :result :one
-- :doc Given an account `username` or `account-id`, return whether the account exists in the table.
SELECT 1 FROM admin_account
--~ (when (:username params)   "WHERE username = :username")
--~ (when (:account-id params) "WHERE id = :account-id")

/* Credentials */

-- :name query-credentials
-- :command :query
-- :result :many
-- :doc Query all credentials associated with `:account-id`.
SELECT api_key, secret_key FROM lrs_credential
WHERE account_id = :account-id

-- :name query-credential-ids
-- :command :query
-- :result :one
-- :doc Query the credential and account IDs associated with `:api-key` and `:secret-key`.
SELECT id AS cred_id, account_id FROM lrs_credential
WHERE api_key = :api-key
AND secret_key = :secret-key

-- :name query-credential-scopes
-- :command :query
-- :result :many
-- :doc Given an API key and a secret API key, return all authorized scopes (including NULL). Returns an empty coll if the credential is not present.
SELECT scope FROM credential_to_scope
WHERE api_key = :api-key
AND secret_key = :secret-key
