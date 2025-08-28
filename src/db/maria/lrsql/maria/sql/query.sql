/* Authority subquery fragments */
-- Solution taken from https://stackoverflow.com/a/66315951

-- :frag maria-auth-subquery
(
  SELECT COUNT(DISTINCT stmt_auth.actor_hash) = :authority-ifi-count
     AND SUM(stmt_auth.actor_hash NOT IN (
--~ (lrsql.maria.record/emit-binary-hashes (:authority-ifis params))
)) = 0
  FROM statement_to_actor stmt_auth
  WHERE stmt_auth.statement_id = stmt.statement_id
    AND stmt_auth.usage = 'Authority'
)

/*
(
  SELECT COUNT(DISTINCT stmt_auth.actor_hash) = :authority-ifi-count
     AND SUM(stmt_auth.actor_hash NOT IN (:v*:authority-ifis)) = 0
  FROM statement_to_actor stmt_auth
  WHERE stmt_auth.statement_id = stmt.statement_id
    AND stmt_auth.usage = 'Authority'
)
*/

/* /\ stmt_auth.usage was casted to actor_usage_enum*/
/* can probably speed up above/below by changing actor_ifi in SUM clauses to actor_hash*/
-- :frag maria-auth-ans-subquery
(
  SELECT COUNT(DISTINCT stmt_auth.actor_hash) = :authority-ifi-count
     AND SUM(stmt_auth.actor_ifi NOT IN (:v*:authority-ifis)) = 0 
  FROM statement_to_actor stmt_auth
  WHERE stmt_auth.statement_id = stmt_a.statement_id
    AND stmt_auth.usage = 'Authority'
)

-- :name check-ns
-- :command :query
-- :result :one
select 
--~ (str "'" *ns* "'")
;

/* /\ stmt_auth.usage was casted to actor_usage_enum*/

/* Single-statement query */

-- :name query-statement
-- :command :query
-- :result :one
-- :doc Query for one statement using statement IDs.
SELECT stmt.payload
FROM xapi_statement stmt
WHERE statement_id = :statement-id
--~ (when (some? (:voided? params)) "AND is_voided = :voided?")
--~ (when (:authority-ifis params)  "AND :frag:maria-auth-subquery")
;

-- :name query-statement-exists
-- :command :query
-- :result :one
-- :doc Check for the existence of a Statement with `:statement-id`. Returns nil iff not found. Includes voided Statements.
SELECT 1 FROM xapi_statement
WHERE statement_id = :statement-id;

/* Multi-statement query */

-- :frag maria-actors-join
INNER JOIN statement_to_actor stmt_actor
ON stmt.statement_id = stmt_actor.statement_id
AND stmt_actor.actor_hash = UNHEX(SHA2(:actor-ifi, 256))
--~ (when-not (:related-actors? params) "AND stmt_actor.usage = 'Actor'")

-- :frag maria-activities-join
INNER JOIN statement_to_activity stmt_activ
ON stmt.statement_id = stmt_activ.statement_id
AND stmt_activ.activity_hash = UNHEX(SHA2(:activity-iri, 256))
--~ (when-not (:related-activities? params) "AND stmt_activ.usage = 'Object'")

-- :frag maria-stmt-no-ref-subquery-frag
SELECT stmt.id, stmt.payload
FROM xapi_statement stmt
--~ (when (:actor-ifi params)    ":frag:maria-actors-join")
--~ (when (:activity-iri params) ":frag:maria-activities-join")
WHERE stmt.is_voided = FALSE
/*~ (when (:from params)
     (if (:ascending? params)      "AND stmt.id >= :from" "AND stmt.id <= :from"))  ~*/
--~ (when (:since params)          "AND stmt.id > :since")
--~ (when (:until params)          "AND stmt.id <= :until")
--~ (when (:verb-iri params)       "AND stmt.verb_hash = UNHEX(SHA2(:verb-iri, 256))")
--~ (when (:registration params)   "AND stmt.registration = :registration")
--~ (when (:authority-ifis params) "AND :frag:maria-auth-subquery")
--~ (if (:ascending? params)       "ORDER BY stmt.id ASC" "ORDER BY stmt.id DESC")
/*  (when (:limit params)          "LIMIT :limit") */

/* Note: We sort by both the PK and statement ID in order to force the query
   planner to avoid scanning on `stmt_a.id` first, which is much slower than
   joining on `statement_to_statement` (at least when the number of such links
   is lower than the number of statements, which is most cases). */

-- :frag maria-stmt-ref-subquery-frag
SELECT stmt_a.id, stmt_a.payload
FROM xapi_statement stmt
--~ (when (:actor-ifi params)    ":frag:maria-actors-join")
--~ (when (:activity-iri params) ":frag:maria-activities-join")
INNER JOIN statement_to_statement sts ON stmt.statement_id = sts.descendant_id
INNER JOIN xapi_statement stmt_a ON sts.ancestor_id = stmt_a.statement_id
WHERE stmt_a.is_voided = FALSE
/*~ (when (:from params)
     (if (:ascending? params)      "AND stmt_a.id >= :from" "AND stmt_a.id <= :from"))  ~*/
--~ (when (:since params)          "AND stmt_a.id > :since")
--~ (when (:until params)          "AND stmt_a.id <= :until")
--~ (when (:verb-iri params)       "AND stmt.verb_hash = UNHEX(SHA2(:verb-iri, 256))")
--~ (when (:registration params)   "AND stmt.registration = :registration")
--~ (when (:authority-ifis params) "AND :frag:maria-auth-ans-subquery")
--~ (when (:authority-ifis params) "AND :frag:maria-auth-subquery")
/*~ (if (:ascending? params)       "ORDER BY stmt_a.id ASC, stmt_a.statement_id ASC"
                                   "ORDER BY stmt_a.id DESC, stmt_a.statement_id DESC") ~*/
/* (when (:limit params)          "LIMIT :limit")*/



-- :name query-statements
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
SELECT all_stmt.id, all_stmt.payload
FROM (
  (:frag:maria-stmt-no-ref-subquery-frag)
  UNION ALL
  (:frag:maria-stmt-ref-subquery-frag))
AS all_stmt
GROUP BY all_stmt.id
--~ (if (:ascending? params) "ORDER BY all_stmt.id ASC" "ORDER BY all_stmt.id DESC")
--~ (when (:limit params)    "LIMIT :limit")


-- :name query-statements-rolling
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
WITH distinct_statements AS (
 SELECT all_stmt.id, all_stmt.payload,
  ROW_NUMBER() OVER (PARTITION BY all_stmt.id) AS rn
 FROM (
 (:frag:maria-stmt-no-ref-subquery-frag)
 UNION ALL
 (:frag:maria-stmt-ref-subquery-frag))
AS all_stmt(id,	payload)
--~ (if (:ascending? params) "ORDER BY all_stmt.id ASC" "ORDER BY all_stmt.id DESC")
--~ (when (:limit params)    "LIMIT :limit")
)
SELECT id, payload
FROM distinct_statements
WHERE rn =1;

-- :name query-statements-postgres
-- :command :query
-- :result :many
-- :doc Query for one or more statements using statement resource parameters.
SELECT DISTINCT ON (all_stmt.id)
  all_stmt.id,
  all_stmt.payload
FROM (
  (:frag:maria-stmt-no-ref-subquery-frag)
  UNION ALL
  (:frag:maria-stmt-ref-subquery-frag))
AS all_stmt
--~ (if (:ascending? params) "ORDER BY all_stmt.id ASC" "ORDER BY all_stmt.id DESC")
--~ (when (:limit params)    "LIMIT :limit")

/* Statement Object Queries */

-- :name query-actor
-- :command :query
-- :result :one
-- :doc Query an actor with `:actor-ifi` and `:actor-type`.
SELECT payload FROM actor
WHERE actor_ifi = :actor-ifi
AND actor_type = :actor-type;

-- :name query-activity
-- :command :query
-- :result :one
-- :doc Query an activity with `:activity-iri`.
SELECT payload FROM activity
WHERE activity_hash = UNHEX(SHA2(:activity-iri, 256));

/* Statement Reference Queries */

-- :name query-statement-descendants
-- :command :query
-- :result :many
-- :doc Query for the descendants of a referencing `:ancestor-id`.
SELECT descendant_id FROM statement_to_statement
WHERE ancestor_id = :ancestor-id;

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
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
AND agent_hash =  UNHEX(SHA2(:agent-ifi,256))
AND state_id = :state-id
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name query-agent-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single agent profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM agent_profile_document
WHERE agent_hash = UNHEX(SHA2(:agent-ifi,256))
AND profile_id = :profile-id;

-- :name query-activity-profile-document
-- :command :query
-- :result :one
-- :doc Query for a single activity profile document using resource params.
SELECT contents, content_type, content_length, profile_id, last_modified
FROM activity_profile_document
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
AND profile_id = :profile-id;
-- :name query-state-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular state document exists. If `:registration` is missing then `registration` must be NULL.
SELECT 1 FROM state_document
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
AND agent_hash = UNHEX(SHA2(:agent-ifi,256))
AND state_id = :state-id
--~ (if (:registration params) "AND registration = :registration" "AND registration IS NULL")
;

-- :name query-agent-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular agent profile document exists.
SELECT 1 FROM agent_profile_document
WHERE agent_hash = UNHEX(SHA2(:agent-ifi,256))
AND profile_id = :profile-id;

-- :name query-activity-profile-document-exists
-- :command :query
-- :result :one
-- :doc Query whether a particular activity profile document exists.
SELECT 1 FROM activity_profile_document
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
AND profile_id = :profile-id;

-- :name query-state-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more state document IDs using resource params. If `:registration` is missing then `registration` must be NULL.
SELECT state_id FROM state_document
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
AND agent_hash = UNHEX(SHA2(:agent-ifi,256))
--~ (when (:registration params) "AND registration = :registration" "AND registration IS NULL")
--~ (when (:since params) "AND last_modified > :since")
;

-- :name query-agent-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more agent profile document profile IDs using resource params.
SELECT profile_id FROM agent_profile_document
WHERE agent_hash = UNHEX(SHA2(:agent-ifi,256))
--~ (when (:since params) "AND last_modified > :since")
;

-- :name query-activity-profile-document-ids
-- :command :query
-- :result :many
-- :doc Query for one or more activity profile document IDs using resource params.
SELECT profile_id FROM activity_profile_document
WHERE activity_hash = UNHEX(SHA2(:activity-iri,256))
--~ (when (:since params) "AND last_modified > :since")
;

/* Admin Accounts */

-- :name query-account
-- :command :query
-- :result :one
-- :doc Given an account `username` or `account-id`, return the ID and the hashed password, which can be used to verify the account.
SELECT id, passhash, username FROM admin_account
--~ (when (:username params)   "WHERE username = :username")
--~ (when (:account-id params) "WHERE id = :account-id")
;

-- :name query-account-oidc
-- :command :query
-- :result :one
-- :doc Given an account `username`, return the ID and OIDC issuer, which can be used to verify the OIDC identity.
SELECT id, oidc_issuer FROM admin_account
WHERE username = :username;

-- :name query-account-by-id
-- :command :query
-- :result :one
-- :doc Given an account `account-id`, return the ID and OIDC issuer, if any.
SELECT id, oidc_issuer FROM admin_account
WHERE id = :account-id;

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
;

-- :name query-account-count-local
-- :command :query
-- :result :one
-- :doc Count the local (non-OIDC) admin accounts present.
SELECT COUNT(id) local_account_count
FROM admin_account
WHERE oidc_issuer IS NULL;

/* Credentials */

-- :name query-credentials
-- :command :query
-- :result :many
-- :doc Query all credentials associated with `:account-id`.
SELECT id, api_key, secret_key, label, is_seed FROM lrs_credential
WHERE account_id = :account-id;

-- :name query-credential-ids
-- :command :query
-- :result :one
-- :doc Query the credential and account IDs associated with `:api-key` and `:secret-key`.
SELECT id AS cred_id, account_id FROM lrs_credential
WHERE api_key = :api-key
AND secret_key = :secret-key;

-- :name query-credential-scopes
-- :command :query
-- :result :many
-- :doc Given an API key and a secret API key, return all authorized scopes (including NULL). Returns an empty coll if the credential is not present.
SELECT scope FROM credential_to_scope
WHERE api_key = :api-key
AND secret_key = :secret-key;

/* LRS Status */

-- :name query-statement-count
-- :command :query
-- :result :one
-- :doc Return the number of statements in the LRS
SELECT COUNT(id) scount
FROM xapi_statement;

-- :name query-actor-count
-- :command :query
-- :result :one
-- :doc Return the number of distinct statement actors
SELECT COUNT(DISTINCT actor_ifi) acount
FROM statement_to_actor
WHERE `usage` = 'Actor';

-- :name query-last-statement-stored
-- :command :query
-- :result :one
-- :doc Return the stored timestamp of the most recent statement
SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.stored')) as lstored
FROM xapi_statement
ORDER BY id DESC
LIMIT 1;


-- :name query-platform-frequency
-- :command :query
-- :result :many
-- :doc Return counts of platforms used in statements.
SELECT COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.context.platform')), 'none') platform,
COUNT(id) scount
FROM xapi_statement
GROUP BY platform;

-- :name query-timeline
-- :command :query
-- :result :many
-- :doc Return counts of statements by time unit for a given range.
SELECT SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.stored')), 1, :unit-for) AS stored_time,
COUNT(id) scount
FROM xapi_statement
WHERE id > :since-id
  AND id <= :until-id
GROUP BY stored_time
ORDER BY stored_time ASC;

/* Statement Reactions */

-- :snip snip-json-extract
JSON_EXTRACT(:i:col,
/*~ (as-> (:path params) path
      (into [\$] path)
      (clojure.string/join \. path)
      (clojure.string/replace path ":" "\\:")
      (str "'" path "'"))
~*/
)

-- :snip snip-json-extract-one
JSON_EXTRACT(:i:col,
--~(format "'%s'"  (clojure.string/join \. (into [\$] (:path params))))
)

-- :snip snip-val
:v:val

-- :snip snip-col
:i:col

-- :snip snip-clause
:snip:left :sql:op :snip:right

-- :snip snip-and
--~ (str "(" (apply str (interpose " AND " (map-indexed (fn [idx _] (str ":snip:clauses." idx)) (:clauses params)))) ")")

-- :snip snip-or
--~ (str "(" (apply str (interpose " OR " (map-indexed (fn [idx _] (str ":snip:clauses." idx)) (:clauses params)))) ")")

-- :snip snip-not
(NOT :snip:clause)

-- :snip snip-contains-json
-- :doc Does the json at col and path contain the given value? A special case with differing structure across backends
JSON_CONTAINS(:i:col, :snip:right,
/*~
(as-> (:path params) path
     (into [\$] path)
     (clojure.string/join \. path)
     (clojure.string/replace path ":" "\\:")
     (str "'" path "'"))
~*/
)

/* (clojure.string/join \. (into [\$] (:path params)))*/

-- :snip snip-query-reaction
SELECT :i*:select
FROM :i*:from
WHERE :snip:where;

-- :name query-reaction
:snip:sql

-- :name query-active-reactions
-- :command :query
-- :result :many
-- :doc Return all active `reaction` ids and rulesets
SELECT id, ruleset
FROM reaction
WHERE active = true;

-- :name query-all-reactions
-- :command :query
-- :result :many
-- :doc Query all active and inactive reactions
SELECT id, title, ruleset, active, created, modified, error
FROM reaction
WHERE active IS NOT NULL;

-- :name query-reaction-history
-- :command :query
-- :result :many
-- :doc For a given statement id, return all reactions (if any) leading to the issuance of that statement.
WITH RECURSIVE trigger_history (statement_id, reaction_id, trigger_id) AS (
  SELECT s.statement_id, s.reaction_id, s.trigger_id
  FROM xapi_statement s
  WHERE s.statement_id = :statement-id
  UNION ALL
  SELECT s.statement_id, s.reaction_id, s.trigger_id
  FROM xapi_statement s
  JOIN trigger_history th ON th.trigger_id = s.statement_id
)
SELECT reaction_id
FROM trigger_history
WHERE reaction_id IS NOT NULL;

/* JWT Blocklist */

-- :name query-blocked-jwt-exists
-- :command :query
-- :result :one
-- :doc Query that `:jwt` is in the blocklist. Excludes JWTs where `one_time_id` is not null.
SELECT 1 FROM blocked_jwt
WHERE jwt = :jwt
AND one_time_id IS NULL;

-- :name query-one-time-jwt-exists
-- :command :query
-- :result :one
-- :doc Query that `:jwt` with `:one-time-id` exists.
SELECT 1 FROM blocked_jwt
WHERE jwt = :jwt
AND one_time_id = :one-time-id;
