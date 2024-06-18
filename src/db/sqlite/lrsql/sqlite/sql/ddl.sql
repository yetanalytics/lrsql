/* Pragmas */

/* We maintain this as a snippet so that we have access to the SQL string
   (as opposed to a HugSql function). */

-- :snip ensure-foreign-keys-snip
PRAGMA foreign_keys = ON;

/* Statement + Attachment Tables */

-- :name create-statement-table!
-- :command :execute
-- :doc Create the `statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id           TEXT NOT NULL PRIMARY KEY ASC,  -- uuid
  statement_id TEXT NOT NULL UNIQUE,           -- uuid
  registration TEXT,                           -- uuid
  verb_iri     TEXT NOT NULL,                  -- iri string
  is_voided    INTEGER DEFAULT FALSE NOT NULL, -- boolean
  payload      BLOB NOT NULL                   -- json
)

-- :name create-desc-id-index!
-- :command :execute
-- :doc Create a second, descending index on the statement primary key.
CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC)

-- :name create-verb-iri-index!
-- :command :execute
-- :doc Create an index on the `xapi_statement.verb_iri` column.
CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri)

-- :name create-registration-index!
-- :command :execute
-- :doc Create an index on the `xapi_statement.registration` column.
CREATE INDEX IF NOT EXISTS registration_idx ON xapi_statement(registration)

-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS actor (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  actor_ifi  TEXT NOT NULL,             -- ifi string
  actor_type TEXT CHECK (
               actor_type IN ('Agent', 'Group')
             ) NOT NULL,                -- enum
  payload    BLOB NOT NULL,             -- json
  UNIQUE (actor_ifi, actor_type)
)

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  activity_iri TEXT NOT NULL UNIQUE,      -- iri string
  payload      BLOB NOT NULL              -- json
)

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS attachment (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id   TEXT NOT NULL,             -- uuid
  attachment_sha TEXT NOT NULL,             -- sha2 string
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id)
)

-- :name create-attachment-statement-id-index!
-- :command :execute
-- :doc Create an index on the `attachment.statement_id` column.
CREATE INDEX IF NOT EXISTS attachment_statement_id_idx ON attachment(statement_id)

-- :name create-statement-to-actor-table!
-- :command :execute
-- :doc Create the `statement_to_actor` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id TEXT NOT NULL,             -- uuid
  usage        TEXT CHECK (
                 usage IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team',
                           'SubActor', 'SubObject', 'SubInstructor', 'SubTeam')
               ) NOT NULL,                -- enum
  actor_ifi    TEXT NOT NULL,             -- ifi string
  actor_type   TEXT CHECK (
                 actor_type IN ('Agent', 'Group')
               ) NOT NULL,                -- enum
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type)
)

-- :name create-statement-actor-statement-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_actor.statement_id` column.
CREATE INDEX IF NOT EXISTS statement_actor_statement_id_idx ON statement_to_actor(statement_id)

-- :name create-statement-actor-ifi-index!
-- :command :execute
-- :doc Create an index on the `statement_to_actor.actor_ifi` column.
CREATE INDEX IF NOT EXISTS statement_actor_ifi_idx ON statement_to_actor(actor_ifi)

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id TEXT NOT NULL,             -- uuid
  usage        TEXT CHECK (
                 usage IN ('Object', 'Category', 'Grouping', 'Parent', 'Other',
                           'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther')
               ) NOT NULL,                -- enum
  activity_iri TEXT NOT NULL,             -- iri string
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (activity_iri) REFERENCES activity(activity_iri)
)

-- :name create-statement-activity-statement-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_activity.statement_id` column.
CREATE INDEX IF NOT EXISTS statement_activity_statement_id_idx ON statement_to_activity(statement_id)

-- :name create-statement-activity-iri-index!
-- :command :execute
-- :doc Create an index on the `statement_to_activity.activity_iri` column.
CREATE INDEX IF NOT EXISTS statement_activity_iri_idx ON statement_to_activity(activity_iri)

-- :name create-statement-to-statement-table!
-- :command :execute
-- :doc Create the `statement_to_statement` link table, used for StatementRef associations, if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_statement (
  id            TEXT NOT NULL PRIMARY KEY, -- uuid
  ancestor_id   TEXT NOT NULL,             -- uuid
  descendant_id TEXT NOT NULL,             -- uuid
  FOREIGN KEY (ancestor_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (descendant_id) REFERENCES xapi_statement(statement_id)
)

-- :name create-sts-ancestor-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_statement.ancestor_id` column.
CREATE INDEX IF NOT EXISTS sts_ancestor_id_idx ON statement_to_statement(ancestor_id)

-- :name create-sts-descendant-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_statement.descendant_id` column.
CREATE INDEX IF NOT EXISTS sts_descendant_id_idx ON statement_to_statement(descendant_id)



/* Document Tables */

-- :name create-state-document-table!
-- :command :execute
-- :doc Create the `state_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS state_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  state_id       TEXT NOT NULL,             -- iri string
  activity_iri   TEXT NOT NULL,             -- iri string
  agent_ifi      TEXT NOT NULL,             -- ifi string
  registration   TEXT,                      -- uuid
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (state_id, activity_iri, agent_ifi, registration) ON CONFLICT IGNORE
)

-- :name create-agent-profile-document-table!
-- :command :execute
-- :doc Create the `agent_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  profile_id     TEXT NOT NULL,             -- iri string
  agent_ifi      TEXT NOT NULL,             -- ifi string
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (profile_id, agent_ifi) ON CONFLICT IGNORE
)

-- :name create-activity-profile-document-table!
-- :command :execute
-- :doc Create the `activity_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  profile_id     TEXT NOT NULL,             -- iri string
  activity_iri   TEXT NOT NULL,             -- iri string
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (profile_id, activity_iri) ON CONFLICT IGNORE
)

/* Admin Account Table */

-- :name create-admin-account-table!
-- :command :execute
-- :doc Create the `admin_account` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS admin_account (
  id       TEXT NOT NULL PRIMARY KEY, -- uuid
  username TEXT NOT NULL UNIQUE,      -- string
  passhash TEXT NOT NULL              -- string
)

/* Credential Tables */

-- :name create-credential-table!
-- :command :execute
-- :doc Create the `lrs_credential` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS lrs_credential (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  api_key    TEXT NOT NULL,             -- hex string
  secret_key TEXT NOT NULL,             -- hex string
  account_id TEXT NOT NULL,             -- uuid
  UNIQUE (api_key, secret_key),
  FOREIGN KEY (account_id)
    REFERENCES admin_account(id)
    ON DELETE CASCADE
)

-- :name create-credential-to-scope-table!
-- :command :execute
-- :doc Create the `credential_to_scope` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS credential_to_scope (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  api_key    TEXT NOT NULL,             -- string
  secret_key TEXT NOT NULL,             -- string
  scope      TEXT CHECK (
               scope IN ('statements/write',
                         'statements/read',
                         'all/read',
                         'all'
             )), -- enum
  FOREIGN KEY (api_key, secret_key)
    REFERENCES lrs_credential(api_key, secret_key)
    ON DELETE CASCADE
)

/* Schema Update */

-- :name enable-writable-schema!
-- :command :execute
-- :doc Enable writing to the sqlite schema.
PRAGMA writable_schema = ON

-- :name disable-writable-schema!
-- :command :execute
-- :doc Disable writing to the sqlite schema.
PRAGMA writable_schema = OFF

-- :name run-integrity-check
-- :command :execute
-- :doc Run the sqlite schema integrity check.
PRAGMA integrity_check

-- :name query-schema-version
-- :command :query
-- :result :one
-- :doc Query the db schema version.
PRAGMA schema_version

-- :name update-schema-version!
-- :command :execute
-- :doc Set the db schema version.
PRAGMA schema_version = :sql:schema_version

/* Migration 2022-02-22-00 - Set admin_account.passhash to optional */

-- :name query-admin-account-passhash-notnull
-- :command :query
-- :result :one
-- :doc Query to see if admin_account passhash is required. Returns 1 if the passhash is not nullable (i.e. not optional).
SELECT 1 FROM pragma_table_info('admin_account')
WHERE name = 'passhash' AND "notnull" = 1 -- non-nullable is true

-- :name alter-admin-account-passhash-optional!
-- :command :execute
-- :doc Set `admin_account.passhash` to optional.
UPDATE sqlite_schema
SET sql='CREATE TABLE admin_account (
  id TEXT NOT NULL PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  passhash TEXT)'
WHERE type = 'table' AND name = 'admin_account'

/* Migration 2022-02-23-00 - Add oidc_issuer to admin_account */

-- :name query-admin-account-oidc-issuer-exists
-- :command :query
-- :result :one
-- :doc Query to see if `admin_account.oidc_issuer` exists.
SELECT 1 FROM pragma_table_info('admin_account') WHERE name = 'oidc_issuer'

-- :name alter-admin-account-add-openid-issuer!
-- :command :execute
-- :doc Add `admin_account.oidc_issuer` to record OIDC identity source.
ALTER TABLE admin_account ADD COLUMN oidc_issuer TEXT

/* Migration 2024-01-24 - Add document/profile and document/profile/read scopes */

/*
 * Other changes:
 * 1. 2022-08-18 - Add statements/read/mine to credential_to_scope.scope enum
 * 2. 2024-05-31 - Add query-scope-enum-updated guard query
 * (The initial version of this function, for change 1, was first deprecated,
 * then removed.)
 */

/* The suggested scope name would simply be profile, but that would clash with
   the reserved OIDC profile scope. Since they have always remained unused, we
   are safe to remove them from the enum table. */

-- :name query-credential-to-scope-scope-datatype-updated
-- :command :query
-- :result :one
-- :doc Query to see if the CHECk constraint of the `credential_to_scope.scope` column has been updated to the latest allowed values. Returns a map of `:scope_enum_updated`.
SELECT 1
FROM (
  SELECT sql
  FROM sqlite_master
  WHERE type='table' AND name='credential_to_scope'
) AS sub_query
WHERE sub_query.sql GLOB (
    '*(''statements/write'','
  || '*''statements/read'','
  || '*''statements/read/mine'','    -- Added 2022-08-18
  || '*''all/read'','
  || '*''all'','
  || '*''define'','                  -- Added 2022-08-18
  || '*''state'','                   -- ""
  || '*''state/read'','              -- ""
  || '*''activities_profile'','      -- Added 2024-01-24
  || '*''activities_profile/read'',' -- ""
  || '*''agents_profile'','          -- ""
  || '*''agents_profile/read'')*'    -- ""
);

-- :name alter-credential-to-scope-scope-datatype!
-- :command :execute
-- :doc Change the enum datatype of the `credential_to_scope.scope` column. Supersedes `alter-credential-to-scope-scope-datatype!`
UPDATE sqlite_schema
SET sql = 'CREATE TABLE credential_to_scope (
  id         TEXT NOT NULL PRIMARY KEY,
  api_key    TEXT NOT NULL,
  secret_key TEXT NOT NULL,
  scope      TEXT CHECK (
               scope IN (''statements/write'',
                         ''statements/read'',
                         ''statements/read/mine'',
                         ''all/read'',
                         ''all'',
                         ''define'',
                         ''state'',
                         ''state/read'',
                         ''activities_profile'',
                         ''activities_profile/read'',
                         ''agents_profile'',
                         ''agents_profile/read'')
             ),
  FOREIGN KEY (api_key, secret_key)
    REFERENCES lrs_credential(api_key, secret_key)
    ON DELETE CASCADE
)'
WHERE type = 'table' AND name = 'credential_to_scope'

/* Migration 2023-05-08-00 - Add timestamp to xapi_statement */

-- :name query-xapi-statement-timestamp-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.timestamp` exists.
SELECT 1 FROM pragma_table_info('xapi_statement') WHERE name = 'timestamp'

-- :name alter-xapi-statement-add-timestamp!
-- :command :execute
-- :doc Add `xapi_statement.timestamp` to allow easier timestamp access.
ALTER TABLE xapi_statement ADD COLUMN timestamp TIMESTAMP

-- :name migrate-xapi-statement-timestamps!
-- :command :execute
-- :doc Backfill `xapi_statement.timestamp` with the values from the payload
UPDATE xapi_statement SET timestamp = strftime('%Y-%m-%dT%H:%M:%f000000Z', json_extract(payload, '$.timestamp'))
WHERE timestamp IS NULL;

/* Migration 2023-05-08-01 - Add stored to xapi_statement */

-- :name query-xapi-statement-stored-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.stored` exists.
SELECT 1 FROM pragma_table_info('xapi_statement') WHERE name = 'stored'

-- :name alter-xapi-statement-add-stored!
-- :command :execute
-- :doc Add `xapi_statement.stored` to allow easier stored time access.
ALTER TABLE xapi_statement ADD COLUMN stored TIMESTAMP

-- :name migrate-xapi-statement-stored-times!
-- :command :execute
-- :doc Backfill `xapi_statement.stored` with the values from the payload
UPDATE xapi_statement SET stored = strftime('%Y-%m-%dT%H:%M:%f000000Z', json_extract(payload, '$.stored'))
WHERE stored IS NULL;

/* Migration 2023-05-11-00 - Convert timestamps for consistency */

-- :name query-state-document-last-modified-is-timestamp
-- :command :query
-- :result :one
-- :doc Query to see if `state_document.last_modified` is a timestamp.
SELECT 1 FROM pragma_table_info('state_document') WHERE name = 'last_modified' AND type = 'TIMESTAMP';

-- :name migrate-timestamps-state-01!
-- :command :execute
-- :doc Convert `state_document.last_modified` to timestamp - 01
ALTER TABLE state_document ADD COLUMN last_modified_tmp TIMESTAMP;
-- :name migrate-timestamps-state-02!
-- :command :execute
-- :doc Convert `state_document.last_modified` to timestamp - 02
UPDATE state_document SET last_modified_tmp = last_modified WHERE last_modified_tmp IS NULL;
-- :name migrate-timestamps-state-03!
-- :command :execute
-- :doc Convert `state_document.last_modified` to timestamp - 03
ALTER TABLE state_document DROP COLUMN last_modified;
-- :name migrate-timestamps-state-04!
-- :command :execute
-- :doc Convert `state_document.last_modified` to timestamp - 04
ALTER TABLE state_document RENAME COLUMN last_modified_tmp TO last_modified;

-- :name migrate-timestamps-agent-profile-01!
-- :command :execute
-- :doc Convert `agent_profile_document.last_modified` to timestamp - 01
ALTER TABLE agent_profile_document ADD COLUMN last_modified_tmp TIMESTAMP;
-- :name migrate-timestamps-agent-profile-02!
-- :command :execute
-- :doc Convert `agent_profile_document.last_modified` to timestamp - 02
UPDATE agent_profile_document SET last_modified_tmp = last_modified WHERE last_modified_tmp IS NULL;
-- :name migrate-timestamps-agent-profile-03!
-- :command :execute
-- :doc Convert `agent_profile_document.last_modified` to timestamp - 03
ALTER TABLE agent_profile_document DROP COLUMN last_modified;
-- :name migrate-timestamps-agent-profile-04!
-- :command :execute
-- :doc Convert `agent_profile_document.last_modified` to timestamp - 04
ALTER TABLE agent_profile_document RENAME COLUMN last_modified_tmp TO last_modified;

-- :name migrate-timestamps-activity-profile-01!
-- :command :execute
-- :doc Convert `activity_profile_document.last_modified` to timestamp - 01
ALTER TABLE activity_profile_document ADD COLUMN last_modified_tmp TIMESTAMP;
-- :name migrate-timestamps-activity-profile-02!
-- :command :execute
-- :doc Convert `activity_profile_document.last_modified` to timestamp - 02
UPDATE activity_profile_document SET last_modified_tmp = last_modified WHERE last_modified_tmp IS NULL;
-- :name migrate-timestamps-activity-profile-03!
-- :command :execute
-- :doc Convert `activity_profile_document.last_modified` to timestamp - 03
ALTER TABLE activity_profile_document DROP COLUMN last_modified;
-- :name migrate-timestamps-activity-profile-04!
-- :command :execute
-- :doc Convert `activity_profile_document.last_modified` to timestamp - 04
ALTER TABLE activity_profile_document RENAME COLUMN last_modified_tmp TO last_modified;

/* Migration 2023-07-21-00 - Add Reaction Table */

-- :name create-reaction-table!
-- :command :execute
-- :doc Create the `reaction` table if it does not yet exist.
CREATE TABLE IF NOT EXISTS reaction (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  title      TEXT NOT NULL UNIQUE,      -- string
  ruleset    BLOB NOT NULL,             -- serialized reaction spec
  created    TIMESTAMP NOT NULL,        -- timestamp
  modified   TIMESTAMP NOT NULL,        -- timestamp
  active     INTEGER,                   -- true/false/null - active/inactive/soft delete
  error      BLOB                       -- serialized error
)

-- :name query-xapi-statement-reaction-id-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.reaction_id` exists.
SELECT 1 FROM pragma_table_info('xapi_statement') WHERE name = 'reaction_id'

-- :name xapi-statement-add-reaction-id!
-- :command :execute
-- :doc Adds `xapi_statement.reaction_id`
ALTER TABLE xapi_statement ADD COLUMN reaction_id TEXT REFERENCES reaction(id);

-- :name xapi-statement-add-trigger-id!
-- :command :execute
-- :doc Adds `xapi_statement.trigger_id`
ALTER TABLE xapi_statement ADD COLUMN trigger_id TEXT REFERENCES xapi_statement(statement_id);

-- :name query-statement-to-actor-has-cascade-delete
-- :command :query
-- :result :one
-- :doc Query to see whether the `statement_to_actor` foreign key in the table `xapi_statement` does CASCADE when its referenced row is deleted.
SELECT 1 FROM pragma_foreign_key_list('statement_to_actor')
WHERE "table" = 'xapi_statement' AND on_delete = 'CASCADE';

-- :name alter-statement-to-actor-add-cascade-delete!
-- :command :execute
UPDATE sqlite_schema
SET sql = 'CREATE TABLE statement_to_actor (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id TEXT NOT NULL,             -- uuid
  usage        TEXT NOT NULL CHECK (
                 usage IN (''Actor'', ''Object'', ''Authority'', ''Instructor'', ''Team'',
                           ''SubActor'', ''SubObject'', ''SubInstructor'', ''SubTeam'')
                                   ),     -- enum
  actor_ifi    TEXT NOT NULL,             -- ifi string
  actor_type   TEXT NOT NULL CHECK (
                 actor_type IN (''Agent'', ''Group'')
                 ),                       -- enum

  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id)
    ON DELETE CASCADE,
  FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type)
)'
WHERE type = 'table' AND name = 'statement_to_actor'
