/* Statement + Attachment Tables */

-- :name create-statement-table!
-- :command :execute
-- :doc Create the `xapi_statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id           CHAR(36) PRIMARY KEY,
  statement_id CHAR(36) NOT NULL UNIQUE,
  registration CHAR(36),
  verb_iri     TEXT NOT NULL,
  verb_hash    BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(verb_iri, 256))) STORED,
  is_voided    BOOLEAN DEFAULT FALSE NOT NULL,
  payload      JSON NOT NULL,
  `timestamp`  TIMESTAMP(6),
  `stored`     TIMESTAMP(6),
  reaction_id  CHAR(36),
  trigger_id   CHAR(36),
  CONSTRAINT stmt_reaction_id_fk FOREIGN KEY (reaction_id) REFERENCES reaction(id),
  CONSTRAINT stmt_trigger_id_fk FOREIGN KEY (trigger_id) REFERENCES xapi_statement(statement_id),
  KEY desc_id_idx (id DESC),
  KEY verb_iri_idx (verb_hash),
  KEY registration (registration),
  KEY stmt_reaction_id_idx (reaction_id),
  KEY stmt_trigger_id_idx (trigger_id)
);


-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS actor (
  id CHAR(36) PRIMARY KEY,
  actor_ifi  TEXT NOT NULL,
  actor_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(actor_ifi, 256))) STORED,
  actor_type ENUM ('Agent', 'Group') NOT NULL,
  payload    JSON NOT NULL,
  CONSTRAINT actor_idx UNIQUE (actor_hash, actor_type)
);

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity (
  id           CHAR(36) PRIMARY KEY,
  activity_iri TEXT NOT NULL,
  activity_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(activity_iri, 256))) STORED UNIQUE,
  payload      JSON NOT NULL
);

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS attachment (
  id             CHAR(36) PRIMARY KEY,
  statement_id   CHAR(36) NOT NULL,
  attachment_sha TEXT NOT NULL,
  content_type   TEXT NOT NULL,
  content_length INTEGER NOT NULL,
  contents       LONGBLOB NOT NULL,
  CONSTRAINT statement_fk_attachment
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  KEY attachment_stmt_fk (statement_id)
);

-- :name create-statement-to-actor-table!
-- :command :execute
-- :doc Create the `statement_to_actor` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           CHAR(36) PRIMARY KEY,
  statement_id CHAR(36) NOT NULL,
  `usage`      ENUM ('Actor',
                     'Object',
                     'Authority',
                     'Instructor',
                     'Team',
                     'SubActor',
                     'SubObject',
                     'SubInstructor',
                     'SubTeam') NOT NULL,
  actor_ifi    TEXT NOT NULL,
  actor_hash   BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(actor_ifi, 256))) STORED,
  actor_type   ENUM ('Agent', 'Group') NOT NULL,
  CONSTRAINT statement_fk_stactor
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id) ON DELETE CASCADE,
  CONSTRAINT actor_fk
    FOREIGN KEY (actor_hash, actor_type) REFERENCES actor(actor_hash, actor_type),
  KEY stmt_actor_stmt_fk (statement_id),
  KEY stmt_actor_actor_fk (actor_hash, actor_type)
);

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           CHAR(36) PRIMARY KEY,
  statement_id CHAR(36) NOT NULL,
  `usage`      ENUM('Object',
                    'Category',
                    'Grouping',
                    'Parent',
                    'Other',
                    'SubObject',
                    'SubCategory',
                    'SubGrouping',
                    'SubParent',
                    'SubOther') NOT NULL,
  activity_iri TEXT NOT NULL,
  activity_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(activity_iri, 256))) STORED,
  CONSTRAINT statement_fk_stactivity
    FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  CONSTRAINT activity_fk
    FOREIGN KEY (activity_hash) REFERENCES activity(activity_hash),
  KEY stmt_activ_stmt_fk (statement_id),
  KEY stmt_activ_activ_fk (activity_hash)
);

-- :name create-statement-to-statement-table!
-- :command :execute
-- :doc Create the `statement_to_statement` link table, used for StatementRef associations, if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_statement (
  id            CHAR(36) PRIMARY KEY,
  ancestor_id   CHAR(36) NOT NULL,
  descendant_id CHAR(36) NOT NULL,
  CONSTRAINT ancestor_fk
    FOREIGN KEY (ancestor_id) REFERENCES xapi_statement(statement_id),
  CONSTRAINT descendant_fk
    FOREIGN KEY (descendant_id) REFERENCES xapi_statement(statement_id),
  KEY stmt_stmt_ans_fk (ancestor_id),
  KEY stmt_stmt_desc_fk (descendant_id)
);

/* Document Tables */

-- :name create-state-document-table!
-- :command :execute
-- :doc Create the `state_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS state_document (
  id             CHAR(36) PRIMARY KEY,
  state_id       TEXT NOT NULL,
  state_hash     BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(state_id, 256))) STORED,
  activity_iri   TEXT NOT NULL,
  activity_hash  BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(activity_iri, 256))) STORED,
  agent_ifi      TEXT NOT NULL,
  agent_hash     BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(agent_ifi, 256))) STORED,
  registration   CHAR(36) DEFAULT NULL,
  last_modified  TIMESTAMP(6) NOT NULL,
  content_type   TEXT NOT NULL,
  content_length INTEGER NOT NULL,
  contents       LONGBLOB NOT NULL,
  CONSTRAINT state_doc_idx
    UNIQUE (state_hash, activity_hash, agent_hash, registration)
);

-- :name create-agent-profile-document-table!
-- :command :execute
-- :doc Create the `agent_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id             CHAR(36) PRIMARY KEY,
  profile_id     TEXT NOT NULL,
  profile_hash   BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(profile_id, 256))) STORED,
  agent_ifi      TEXT NOT NULL,
  agent_hash     BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(agent_ifi, 256))) STORED,
  last_modified  TIMESTAMP(6) NOT NULL,
  content_type   VARCHAR(191) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       LONGBLOB NOT NULL,
  CONSTRAINT agent_profile_doc_idx
  UNIQUE (profile_hash, agent_hash)
);

-- :name create-activity-profile-document-table!
-- :command :execute
-- :doc Create the `activity_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id             CHAR(36) PRIMARY KEY,
  profile_id     TEXT NOT NULL,
  profile_hash   BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(profile_id, 256))) STORED,
  activity_iri   TEXT NOT NULL,
  activity_hash  BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(activity_iri, 256))) STORED,
  last_modified  TIMESTAMP(6) NOT NULL, -- todo---mimics WITH TIME ZONE functionality?
  content_type   TEXT NOT NULL,
  content_length INTEGER NOT NULL,
  contents       LONGBLOB NOT NULL,
  CONSTRAINT activity_profile_doc_idx
    UNIQUE (profile_hash, activity_hash)
);

/* Admin Account Table */

-- :name create-admin-account-table!
-- :command :execute
-- :doc Create the `admin_account` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS admin_account (
  id       CHAR(36) PRIMARY KEY,
  username TEXT NOT NULL,
  username_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(username, 256))) STORED,
  passhash TEXT,
  oidc_issuer TEXT,
  CONSTRAINT admin_account_username_idx UNIQUE (username_hash)
);

/* Credential Tables */

-- :name create-credential-table!
-- :command :execute
-- :doc Create the `lrs_credential` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS lrs_credential (
  id         CHAR(36) PRIMARY KEY,
  api_key    VARCHAR(191) NOT NULL,
  secret_key VARCHAR(191) NOT NULL,
  account_id CHAR(36) NOT NULL,
  is_seed BOOLEAN,
  label TEXT,
  CONSTRAINT credential_idx
    UNIQUE (api_key, secret_key),
  CONSTRAINT account_fk
    FOREIGN KEY (account_id)
    REFERENCES admin_account(id)
    ON DELETE CASCADE,
  KEY cred_account_fk (account_id)
);

-- :name create-credential-to-scope-table!
-- :command :execute
-- :doc Create the `credential_to_scope` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS credential_to_scope (
  id         CHAR(36) PRIMARY KEY,
  api_key    VARCHAR(191) NOT NULL,
  secret_key VARCHAR(191) NOT NULL,
  scope ENUM (
          'statements/write',
          'statements/read',
          'statements/read/mine',
          'all/read',
          'all',
          'state',
          'state/read',
          'define',
          'activities_profile',
          'activities_profile/read',
          'agents_profile',
          'agents_profile/read'),

  CONSTRAINT credential_fk
    FOREIGN KEY (api_key, secret_key)
    REFERENCES lrs_credential(api_key, secret_key)
    ON DELETE CASCADE,
  KEY cred_keypair_fk (api_key, secret_key)
);


-- :name create-reaction-table!
-- :command :execute
-- :doc Create the `reaction` table if it does not yet exist.
CREATE TABLE IF NOT EXISTS reaction (
  id           CHAR(36) PRIMARY KEY,
  title        TEXT NOT NULL, -- string title
  title_hash   BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(title, 256))) STORED,
  ruleset      JSON NOT NULL,                -- serialized reaction spec
  created      TIMESTAMP(6) NOT NULL,           -- timestamp
  modified     TIMESTAMP(6) NOT NULL,           -- timestamp
  active       BOOLEAN,                      -- true/false/null - active/inactive/soft delete
  error        JSON,                         -- serialized error
  CONSTRAINT reaction_title_idx UNIQUE (title_hash)
);

-- :name create-blocked-jwt-table!
-- :command :execute
-- :doc Create the `blocked_jwt` table and associated indexes if they do not exist yet.
CREATE TABLE IF NOT EXISTS blocked_jwt (
  jwt        CHAR(44) PRIMARY KEY,
  evict_time TIMESTAMP(6) NOT NULL,
  one_time_id CHAR(36) UNIQUE,
  KEY blocked_jwt_evict_time_idx (evict_time)
);

/* Migration 2025-09-26 - Add ContextAgent, ContextGroup, SubContextAgent, SubContextGroup to statement_to_actor usage enum */

-- :name query-statement-to-actor-usage-enum-has-context-actors
-- :command :query
-- :result :one
-- :doc Query to see if the `statement_to_actor.usage` column has ContextAgent in its enum.
SELECT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'statement_to_actor'
      AND COLUMN_NAME = 'usage'
      AND FIND_IN_SET('ContextAgent', REPLACE(REPLACE(COLUMN_TYPE, 'enum(', ''), ')', '')) > 0
) AS has_context_agent;

-- :name alter-statement-to-actor-usage-enum-add-context-actors!
-- :command :execute
-- :doc Change the enum datatype of the `statement_to_actor.usage` column to add ContextAgent, ContextGroup, SubContextAgent, and SubContextGroup.
ALTER TABLE statement_to_actor MODIFY COLUMN `usage` ENUM ('Actor',
                     'Object',
                     'Authority',
                     'Instructor',
                     'Team',
                     'SubActor',
                     'SubObject',
                     'SubInstructor',
                     'SubTeam',
                     'ContextAgent',
                     'ContextGroup',
                     'SubContextAgent',
                     'SubContextGroup') NOT NULL;
