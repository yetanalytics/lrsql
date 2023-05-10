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

/* Migration 2022-08-18-00 - Add statements/read/mine to credential_to_scope.scope enum */

/* The obvious way would be to execute ALTER TYPE ... ADD VALUE but that will
   fail inside a transaction. Therefore we have to use this circuitous route:
   https://stackoverflow.com/a/56376907 */


-- :name alter-scope-enum-type!
-- :command :execute
-- :doc Add `statements/read/mine` to `credential-to-scope.scope` enum.
ALTER TABLE IF EXISTS credential_to_scope ALTER COLUMN scope TYPE VARCHAR(255);
DROP TYPE IF EXISTS scope_enum;
CREATE TYPE scope_enum AS ENUM (
  'statements/write',
  'statements/read',
  'statements/read/mine', -- new
  'all/read',
  'all',
   -- unimplemented, but added for future-proofing
   -- state/read and profile/read are not listed in spec, but make logical sense
  'define',
  'state',
  'state/read',
  'profile',
  'profile/read');
ALTER TABLE IF EXISTS credential_to_scope ALTER COLUMN scope TYPE scope_enum USING (scope::scope_enum);


/* Migration 2022-05-08-00 - Add timestamp to xapi_statement */

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

/* Migration 2022-05-08-01 - Add stored to xapi_statement */

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
