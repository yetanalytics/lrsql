/* Enums */

-- Solution from: https://stackoverflow.com/a/48382296

-- :name create-actor-type-enum!
-- :command :execute
-- :doc Create the Actor type enum if it doesn't exist in the current schema.
DO $$ BEGIN
  CREATE TYPE actor_type_enum AS ENUM ('Agent', 'Group');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

-- :name create-actor-usage-enum!
-- :command :execute
-- :doc Create the Actor usage enum if it doesn't exist in the current schema.
DO $$ BEGIN
  CREATE TYPE actor_usage_enum AS ENUM (
      'Actor', 'Object', 'Authority', 'Instructor', 'Team',
      'SubActor', 'SubObject', 'SubInstructor', 'SubTeam');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

-- :name create-activity-usage-enum!
-- :command :execute
-- :doc Create the Activity type enum if it doesn't exist in the current schema.
DO $$ BEGIN
  CREATE TYPE activity_usage_enum AS ENUM (
      'Object', 'Category', 'Grouping', 'Parent', 'Other',
      'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

-- :name create-scope-enum!
-- :command :execute
-- :doc Create the scope enum if it doesn't exist in the current schema.
DO $$ BEGIN
  CREATE TYPE scope_enum AS ENUM (
      'statements/write',
      'statements/read',
      'all/read',
      'all');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

/* Statement + Attachment Tables */

-- :name create-statement-table!
-- :command :execute
-- :doc Create the `xapi_statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id           UUID PRIMARY KEY,
  statement_id UUID NOT NULL UNIQUE,
  registration UUID,
  verb_iri     VARCHAR(255) NOT NULL,
  is_voided    BOOLEAN DEFAULT FALSE NOT NULL,
  payload      JSON NOT NULL -- faster read/write than JSONB
);
CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC);
CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri);
CREATE INDEX IF NOT EXISTS registration ON xapi_statement(registration);

-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS actor (
  id UUID    PRIMARY KEY,
  actor_ifi  VARCHAR(255) NOT NULL,
  actor_type actor_type_enum NOT NULL,
  payload    JSON NOT NULL,
  CONSTRAINT actor_idx UNIQUE (actor_ifi, actor_type)
);

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity (
  id           UUID PRIMARY KEY,
  activity_iri VARCHAR(255) NOT NULL UNIQUE,
  payload      JSON NOT NULL
);

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS attachment (
  id             UUID PRIMARY KEY,
  statement_id   UUID NOT NULL,
  attachment_sha VARCHAR(255) NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BYTEA NOT NULL,
  CONSTRAINT statement_fk
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id)
);
CREATE INDEX IF NOT EXISTS attachment_stmt_fk ON attachment(statement_id);

-- :name create-statement-to-actor-table!
-- :command :execute
-- :doc Create the `statement_to_actor` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           UUID PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        actor_usage_enum NOT NULL,
  actor_ifi    VARCHAR(255) NOT NULL,
  actor_type   actor_type_enum NOT NULL,
  CONSTRAINT statement_fk
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  CONSTRAINT actor_fk
    FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type)
);
CREATE INDEX IF NOT EXISTS stmt_actor_stmt_fk ON statement_to_actor(statement_id);
CREATE INDEX IF NOT EXISTS stmt_actor_actor_fk ON statement_to_actor(actor_ifi, actor_type);

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           UUID PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        activity_usage_enum NOT NULL,
  activity_iri VARCHAR(255) NOT NULL,
  CONSTRAINT statement_fk
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  CONSTRAINT activity_fk
    FOREIGN KEY (activity_iri) REFERENCES activity(activity_iri)
);
CREATE INDEX IF NOT EXISTS stmt_activ_stmt_fk ON statement_to_activity(statement_id);
CREATE INDEX IF NOT EXISTS stmt_activ_activ_fk ON statement_to_activity(activity_iri);

-- :name create-statement-to-statement-table!
-- :command :execute
-- :doc Create the `statement_to_statement` link table, used for StatementRef associations, if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_statement (
  id            UUID PRIMARY KEY,
  ancestor_id   UUID NOT NULL,
  descendant_id UUID NOT NULL,
  CONSTRAINT ancestor_fk
    FOREIGN KEY (ancestor_id) REFERENCES xapi_statement(statement_id),
  CONSTRAINT descendant_fk
    FOREIGN KEY (descendant_id) REFERENCES xapi_statement(statement_id)
);
CREATE INDEX IF NOT EXISTS stmt_stmt_ans_fk ON statement_to_statement(ancestor_id);
CREATE INDEX IF NOT EXISTS stmt_stmt_desc_fk ON statement_to_statement(descendant_id);

/* Document Tables */

-- :name create-state-document-table!
-- :command :execute
-- :doc Create the `state_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS state_document (
  id             UUID PRIMARY KEY,
  state_id       VARCHAR(255) NOT NULL,
  activity_iri   VARCHAR(255) NOT NULL,
  agent_ifi      VARCHAR(255) NOT NULL,
  registration   UUID DEFAULT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BYTEA NOT NULL,
  CONSTRAINT state_doc_idx
    UNIQUE (state_id, activity_iri, agent_ifi, registration)
);

-- :name create-agent-profile-document-table!
-- :command :execute
-- :doc Create the `agent_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id             UUID PRIMARY KEY,
  profile_id     VARCHAR(255) NOT NULL,
  agent_ifi      VARCHAR(255) NOT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BYTEA NOT NULL,
  CONSTRAINT agent_profile_doc_idx
    UNIQUE (profile_id, agent_ifi)
);

-- :name create-activity-profile-document-table!
-- :command :execute
-- :doc Create the `activity_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id             UUID PRIMARY KEY,
  profile_id     VARCHAR(255) NOT NULL,
  activity_iri   VARCHAR(255) NOT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BYTEA NOT NULL,
  CONSTRAINT activity_profile_doc_idx
    UNIQUE (profile_id, activity_iri)
);

/* Admin Account Table */

-- :name create-admin-account-table!
-- :command :execute
-- :doc Create the `admin_account` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS admin_account (
  id       UUID PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  passhash VARCHAR(255) NOT NULL
);

/* Credential Tables */

-- :name create-credential-table!
-- :command :execute
-- :doc Create the `lrs_credential` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS lrs_credential (
  id         UUID PRIMARY KEY,
  api_key    VARCHAR(255) NOT NULL,
  secret_key VARCHAR(255) NOT NULL,
  account_id UUID NOT NULL,
  CONSTRAINT credential_idx
    UNIQUE (api_key, secret_key),
  CONSTRAINT account_fk
    FOREIGN KEY (account_id)
    REFERENCES admin_account(id)
    ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS cred_account_fk ON lrs_credential(account_id);

-- :name create-credential-to-scope-table!
-- :command :execute
-- :doc Create the `credential_to_scope` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS credential_to_scope (
  id         UUID PRIMARY KEY,
  api_key    VARCHAR(255) NOT NULL,
  secret_key VARCHAR(255) NOT NULL,
  scope      scope_enum, -- enum is nullable
  CONSTRAINT credential_fk
    FOREIGN KEY (api_key, secret_key)
    REFERENCES lrs_credential(api_key, secret_key)
    ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS cred_keypair_fk ON credential_to_scope(api_key, secret_key);

/* Migration 2022-02-22-00 - Set admin_account.passhash to optional */

-- :name alter-admin-account-passhash-optional!
-- :command :execute
-- :doc Set `admin_account.passhash` to optional.
ALTER TABLE IF EXISTS admin_account ALTER COLUMN passhash DROP NOT NULL;

/* Migration 2022-02-23-00 - Add oidc_issuer to admin_account */

-- :name alter-admin-account-add-openid-issuer!
-- :command :execute
-- :doc Add `admin_account.oidc_issuer` to record OIDC identity source.
ALTER TABLE IF EXISTS admin_account ADD COLUMN IF NOT EXISTS oidc_issuer VARCHAR(255);

/* Migration 2024-01-24 - Add profile and profile/read scopes */

/*
 * Other changes:
 * 1. 2022-08-18 - Add statements/read/mine to credential_to_scope.scope enum
 * 2. 2024-05-31 - Add query-scope-enum-updated guard query
 * (The initial version of this function, for change 1, was first deprecated,
 * then removed.)
 */

/* The obvious way would be to execute ALTER TYPE ... ADD VALUE but that will
   fail inside a transaction. Therefore we have to use this circuitous route:
   https://stackoverflow.com/a/56376907 */

/* Simply adding the profile scope will cause a name clash with the reserved
   OIDC profile scope. As a result, we add prefixes to create activity_profile
   and agent_profile (and their read-only versions); this has the additional
   benefit of narrowing the scope to just activity and agent profiles,
   respectively. Since profile and profile/read have always been unused, we
   are safe to remove them as enums. */

-- :name query-scope-enum-updated
-- :command :query
-- :result :one
-- :doc Query to see if the DB's current value of `scope_enum` is equal to the one in `alter-scope-enum-type!`. The order of enum values is considered. Returns `nil` if the enum is not updated.
SELECT 1
WHERE enum_range(NULL::scope_enum)::TEXT[]
  = ARRAY[
    'statements/write',
    'statements/read',
    'statements/read/mine',    -- Added 2022-08-18
    'all/read',
    'all',
    'state',                   -- Added 2022-08-18
    'state/read',              -- ""
    'define',                  -- ""
    'activities_profile',      -- Added 2024-01-24
    'activities_profile/read', -- ""
    'agents_profile',          -- ""
    'agents_profile/read'      -- ""
  ];

-- :name alter-scope-enum-type!
-- :command :execute
-- :doc Add `activity_profile`, `activity_profile/read`, `agent_profile`, and `agent_profile/read` to `credential-to-scope.scope` enum. Supersedes `alter-scope-enum-type!`
ALTER TABLE IF EXISTS credential_to_scope ALTER COLUMN scope TYPE VARCHAR(255);
DROP TYPE IF EXISTS scope_enum;
CREATE TYPE scope_enum AS ENUM (
  'statements/write',
  'statements/read',
  'statements/read/mine',    -- Added 2022-08-18
  'all/read',
  'all',
  'state',                   -- Added 2022-08-18
  'state/read',              -- ""
  'define',                  -- ""
  'activities_profile',      -- Added 2024-01-24
  'activities_profile/read', -- ""
  'agents_profile',          -- ""
  'agents_profile/read');    -- ""
ALTER TABLE IF EXISTS credential_to_scope ALTER COLUMN scope TYPE scope_enum USING (scope::scope_enum);

/* Migration 2023-05-08-00 - Add timestamp to xapi_statement */

-- :name query-xapi-statement-timestamp-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.timestamp` exists.
SELECT 1 FROM information_schema.columns WHERE table_name = 'xapi_statement' AND column_name = 'timestamp';

-- :name alter-xapi-statement-add-timestamp!
-- :command :execute
-- :doc Add `xapi_statement.timestamp` to allow easier timestamp access.
ALTER TABLE xapi_statement ADD COLUMN timestamp TIMESTAMPTZ

-- :name migrate-xapi-statement-timestamps!
-- :command :execute
-- :doc Backfill `xapi_statement.timestamp` with the values from the payload
UPDATE xapi_statement SET timestamp = (payload->>'timestamp')::timestamptz WHERE timestamp IS NULL;

/* Migration 2023-05-08-01 - Add stored to xapi_statement */

-- :name query-xapi-statement-stored-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.stored` exists.
SELECT 1 FROM information_schema.columns WHERE table_name = 'xapi_statement' AND column_name = 'stored';

-- :name alter-xapi-statement-add-stored!
-- :command :execute
-- :doc Add `xapi_statement.stored` to allow easier stored time access.
ALTER TABLE xapi_statement ADD COLUMN stored TIMESTAMPTZ

-- :name migrate-xapi-statement-stored-times!
-- :command :execute
-- :doc Backfill `xapi_statement.stored` with the values from the payload
UPDATE xapi_statement SET stored = (payload->>'stored')::timestamptz WHERE stored IS NULL;

/* Migration 2023-05-11-00 - Convert timestamps for consistency */

-- :name query-state-document-last-modified-is-timestamptz
-- :command :query
-- :result :one
-- :doc Query to see if `state_document.last_modified` is a timestamp.
SELECT * FROM information_schema.columns
WHERE table_name = 'state_document' AND column_name = 'last_modified' AND data_type = 'timestamp with time zone';

-- :name migrate-state-document-last-modified!
-- :command :execute
-- :doc Migrate the `state_document.last_modified` to have a timezone, and use the provided timezone to work backwards to Zulu
ALTER TABLE state_document ALTER COLUMN last_modified TYPE TIMESTAMP WITH TIME ZONE
USING last_modified AT TIME ZONE :sql:tz-id;

-- :name migrate-activity-profile-document-last-modified!
-- :command :execute
-- :doc Migrate the `activity_profile_document.last_modified` to have a timezone, and use the provided timezone to work backwards to Zulu
ALTER TABLE activity_profile_document ALTER COLUMN last_modified TYPE TIMESTAMP WITH TIME ZONE
USING last_modified AT TIME ZONE :sql:tz-id;

-- :name migrate-agent-profile-document-last-modified!
-- :command :execute
-- :doc Migrate the `agent_profile_document.last_modified` to have a timezone, and use the provided timezone to work backwards to Zulu
ALTER TABLE agent_profile_document ALTER COLUMN last_modified TYPE TIMESTAMP WITH TIME ZONE
USING last_modified AT TIME ZONE :sql:tz-id;

-- :name migrate-to-jsonb!
-- :command :execute
-- :doc Convert all JSON payloads to JSONB to allow for faster reads and advanced indexing
ALTER TABLE xapi_statement ALTER COLUMN payload SET DATA TYPE JSONB;
ALTER TABLE actor ALTER COLUMN payload SET DATA TYPE JSONB;
ALTER TABLE activity ALTER COLUMN payload SET DATA TYPE JSONB;

-- :name migrate-to-json!
-- :command :execute
-- :doc Convert all JSONB payloads to JSON (or no-op) for faster writes
ALTER TABLE xapi_statement ALTER COLUMN payload SET DATA TYPE JSON;
ALTER TABLE actor ALTER COLUMN payload SET DATA TYPE JSON;
ALTER TABLE activity ALTER COLUMN payload SET DATA TYPE JSON;

/* Migration 2023-07-21-00 - Add Reaction Table */

-- :name create-reaction-table!
-- :command :execute
-- :doc Create the `reaction` table if it does not yet exist.
CREATE TABLE IF NOT EXISTS reaction (
  id           UUID PRIMARY KEY,
  title        VARCHAR(255) NOT NULL UNIQUE, -- string title
  ruleset      JSON NOT NULL,                -- serialized reaction spec
  created      TIMESTAMP NOT NULL,           -- timestamp
  modified     TIMESTAMP NOT NULL,           -- timestamp
  active       BOOLEAN,                      -- true/false/null - active/inactive/soft delete
  error        JSON                          -- serialized error
);

-- :name query-xapi-statement-reaction-id-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.reaction_id` exists.
SELECT 1 FROM information_schema.columns WHERE table_name = 'xapi_statement' AND column_name = 'reaction_id';

-- :name xapi-statement-add-reaction-id!
-- :command :execute
-- :doc Adds `xapi_statement.reaction_id` and associated fk and index
ALTER TABLE xapi_statement ADD COLUMN reaction_id UUID;
ALTER TABLE xapi_statement ADD CONSTRAINT stmt_reaction_id_fk FOREIGN KEY (reaction_id) REFERENCES reaction(id);
CREATE INDEX IF NOT EXISTS stmt_reaction_id_idx ON xapi_statement(reaction_id);

-- :name query-xapi-statement-trigger-id-exists
-- :command :query
-- :result :one
-- :doc Query to see if `xapi_statement.trigger_id` exists.
SELECT 1 FROM information_schema.columns WHERE table_name = 'xapi_statement' AND column_name = 'trigger_id';

-- :name xapi-statement-add-trigger-id!
-- :command :execute
-- :doc Adds `xapi_statement.trigger_id` and associated fk and index
ALTER TABLE xapi_statement ADD COLUMN trigger_id UUID;
ALTER TABLE xapi_statement ADD CONSTRAINT stmt_trigger_id_fk FOREIGN KEY (trigger_id) REFERENCES xapi_statement(statement_id);
CREATE INDEX IF NOT EXISTS stmt_trigger_id_idx ON xapi_statement(trigger_id);

-- :name check-statement-to-actor-cascading-delete
-- :result :one
-- :command :execute
SELECT 1
FROM pg_constraint
WHERE conname = 'statement_fk'
AND pg_get_constraintdef(oid) LIKE '%ON DELETE CASCADE%'

-- :name add-statement-to-actor-cascading-delete!
-- :command :execute
-- :doc Adds a cascading delete to delete st2actor entries when corresponding statements are deleted
ALTER TABLE statement_to_actor DROP CONSTRAINT statement_fk;
ALTER TABLE statement_to_actor ADD CONSTRAINT statement_fk FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id) ON DELETE CASCADE;

/* Migration 2024-05-29 - Universally Convert VARCHAR to TEXT */

-- :name query-varchar-exists
-- :command :query
-- :result :one
-- :doc Query to see if varchar->text conversion has not happened yet.
SELECT 1 FROM information_schema.columns WHERE table_name = 'xapi_statement' AND column_name = 'verb_iri' and data_type = 'character varying';

-- :name convert-varchars-to-text!
-- :command :execute
-- :doc Converts all known VARCHAR(255) fields into TEXT fields. Order of execution is critical for ifi constraints
ALTER TABLE xapi_statement ALTER COLUMN verb_iri TYPE TEXT;

-- Must drop constraints containing ifi (and rebuild after conversion) because conversion in place does not work for actor_fk or actor_idx composites
ALTER TABLE statement_to_actor DROP CONSTRAINT actor_fk;
ALTER TABLE actor DROP CONSTRAINT actor_idx;
ALTER TABLE actor ALTER COLUMN actor_ifi TYPE TEXT;
ALTER TABLE actor ADD CONSTRAINT actor_idx UNIQUE (actor_ifi, actor_type);
ALTER TABLE statement_to_actor ALTER COLUMN actor_ifi TYPE TEXT;
ALTER TABLE statement_to_actor ADD CONSTRAINT actor_fk FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type);

ALTER TABLE activity ALTER COLUMN activity_iri TYPE TEXT;
ALTER TABLE attachment ALTER COLUMN attachment_sha TYPE TEXT;
ALTER TABLE attachment ALTER COLUMN content_type TYPE TEXT;
ALTER TABLE statement_to_activity ALTER COLUMN activity_iri TYPE TEXT;
ALTER TABLE state_document ALTER COLUMN state_id TYPE TEXT;
ALTER TABLE state_document ALTER COLUMN activity_iri TYPE TEXT;
ALTER TABLE state_document ALTER COLUMN agent_ifi TYPE TEXT;
ALTER TABLE state_document ALTER COLUMN content_type TYPE TEXT;
ALTER TABLE activity_profile_document ALTER COLUMN profile_id TYPE TEXT;
ALTER TABLE activity_profile_document ALTER COLUMN activity_iri TYPE TEXT;
ALTER TABLE activity_profile_document ALTER COLUMN content_type TYPE TEXT;
ALTER TABLE admin_account ALTER COLUMN username TYPE TEXT;
ALTER TABLE admin_account ALTER COLUMN passhash TYPE TEXT;
ALTER TABLE admin_account ALTER COLUMN oidc_issuer TYPE TEXT;
ALTER TABLE lrs_credential ALTER COLUMN api_key TYPE TEXT;
ALTER TABLE lrs_credential ALTER COLUMN secret_key TYPE TEXT;
ALTER TABLE credential_to_scope ALTER COLUMN api_key TYPE TEXT;
ALTER TABLE credential_to_scope ALTER COLUMN secret_key TYPE TEXT;
ALTER TABLE reaction ALTER COLUMN title TYPE TEXT;

/* Migration 2024-10-31 - Add JWT Blocklist Table */

-- :name create-blocked-jwt-table!
-- :command :execute
-- :doc Create the `blocked_jwt` table and associated indexes if they do not exist yet.
CREATE TABLE IF NOT EXISTS blocked_jwt (
  jwt        TEXT PRIMARY KEY,
  evict_time TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS blocked_jwt_evict_time_idx ON blocked_jwt(evict_time);

/* Migration 2025-03-05 - Add One-Time ID to Blocklist Table */

-- :name alter-blocked-jwt-add-one-time-id!
-- :command :execute
-- :doc Add the column `blocked_jwt.one_time_id` for one-time JWTs; JWTs with one-time IDs are not considered blocked yet.
ALTER TABLE IF EXISTS blocked_jwt ADD COLUMN IF NOT EXISTS one_time_id TYPE UUID UNIQUE;
