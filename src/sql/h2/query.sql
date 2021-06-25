/* Statement Queries */

-- :name query-statement
-- :command :query
-- :result :one
-- :doc Query for one statement using statement IDs.
SELECT payload FROM xapi_statement
WHERE statement_id = :statement-id
AND is_voided = :voided?

-- :name query-statement-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of a Statement with `:statement-id`. Returns nil iff not found. Includes voided Statements.
SELECT 1 FROM xapi_statement
WHERE statement_id = :statement-id

/* The strategy of `query-statements` is to use multiple joins to form a
   Cartesian product over statements, agents, and activities:
   
     (stmt, stmt_desc, stmt_actor, stmt_desc_actor, stmt_activity, stmt_desc_activity)
   
   Because we only want to return properties of `stmt`, which may be identical
   across multiple such tuples, we apply SELECT DISTINCT at the top level.
*/

/* `id` (the primary key) is a SQUUID with guarenteed monotonicity. This is
   important for the following:
   - As a cursor to the next page of query results when `limit` is applied.
     `id` thus must always be sequential; otherwise, for instance, new
     statements may be inserted mid-page instead of appended.
   - As a secondary sort property for query results; we cannot only apply
     `stored` as the only property since it only has millisecond resolution,
     which is not good enough for deterministic results.
*/

-- :name query-statements
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
SELECT DISTINCT stmt.id, stmt.stored, stmt.payload
FROM xapi_statement stmt
  LEFT JOIN statement_to_statement
    ON stmt.statement_id = statement_to_statement.ancestor_id
  LEFT JOIN xapi_statement stmt_desc
    ON stmt_desc.statement_id = statement_to_statement.descendant_id
  /*~
  (when (:actor-ifi params)
    (str "  LEFT JOIN statement_to_actor stmt_actor\n"
         "    ON stmt.statement_id = stmt_actor.statement_id\n"
         "  LEFT JOIN statement_to_actor stmt_desc_actor\n"
         "    ON stmt_desc.statement_id = stmt_desc_actor.statement_id"))
  ~*/
  /*~
  (when (:activity-iri params)
    (str "  LEFT JOIN statement_to_activity stmt_activ\n"
         "    ON stmt.statement_id = stmt_activ.statement_id\n"
         "  LEFT JOIN statement_to_activity stmt_desc_activ\n"
         "    ON stmt_desc.statement_id = stmt_desc_activ.statement_id"))
  ~*/
WHERE stmt.is_voided = FALSE
  --~ (when (:from params)  "AND stmt.id >= :from")
  --~ (when (:since params) "AND stmt.stored > :since")
  --~ (when (:until params) "AND stmt.stored <= :until")
  AND ((
    1
    --~ (when (:verb-iri params)     "AND stmt.verb_iri = :verb-iri")
    --~ (when (:registration params) "AND stmt.registration = :registration")
    --~ (when (:actor-ifi params)    "AND stmt_actor.actor_ifi = :actor-ifi")
    --~ (when (:activity-iri params) "AND stmt_activ.activity_iri = :activity-iri")
    /*~ (when (and (:actor-ifi params) (not (:related-actors? params)))
          "AND stmt_actor.usage = 'Actor'") ~*/
    /*~ (when (and (:activity-iri params) (not (:related-activities? params)))
          "AND stmt_activ.usage = 'Object'") ~*/
  ) OR (
    1
    --~ (when (:verb-iri params)     "AND stmt_desc.verb_iri = :verb-iri")
    --~ (when (:registration params) "AND stmt_desc.registration = :registration")
    --~ (when (:actor-ifi params)    "AND stmt_desc_actor.actor_ifi = :actor-ifi")
    --~ (when (:activity-iri params) "AND stmt_desc_activ.activity_iri = :activity-iri")
    /*~ (when (and (:actor-ifi params) (not (:related-actors? params)))
          "AND stmt_desc_actor.usage = 'Actor'") ~*/
    /*~ (when (and (:activity-iri params) (not (:related-activities? params)))
          "AND stmt_desc_activ.usage = 'Object'") ~*/
  ))
/*~ (if (:ascending? params)
      "ORDER BY stmt.stored ASC, stmt.id ASC"
      "ORDER BY stmt.stored DESC, stmt.id DESC") ~*/
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

-- :name query-account-exists
-- :command :query
-- :result :one
-- :doc Given an account `username`, return whether the account exists in the table.
SELECT 1 FROM admin_account
WHERE username = :username

/* Credentials */

-- :name query-credentials
-- :command :query
-- :result :many
-- :doc Query all credentials associated with `:account-id`.
SELECT api_key, secret_key FROM lrs_credential
WHERE account_id = :account-id

-- :name query-credential-scopes
-- :command :query
-- :result :many
-- :doc Given an API key and a secret API key, return all authorized scopes (including NULL). Returns an empty coll if the credential is not present.
SELECT scope FROM credential_to_scope
WHERE api_key = :api-key
AND secret_key = :secret-key
