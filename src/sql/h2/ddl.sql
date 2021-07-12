/* Statement + Attachment Tables */

-- :name create-statement-table!
-- :command :execute
-- :doc Create the `statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id               UUID NOT NULL PRIMARY KEY,
  statement_id     UUID NOT NULL UNIQUE,
  registration     UUID,
  verb_iri         VARCHAR(255) NOT NULL,
  is_voided        BOOLEAN DEFAULT FALSE NOT NULL,
  payload          JSON NOT NULL
);
CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC);
CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri);
CREATE INDEX IF NOT EXISTS registration_idx ON xapi_statement(registration)

-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS actor (
  id         UUID NOT NULL PRIMARY KEY,
  actor_ifi  VARCHAR(255) NOT NULL,
  actor_type ENUM('Agent', 'Group') NOT NULL,
  payload    JSON NOT NULL,
  UNIQUE (actor_ifi, actor_type)
)

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity (
  id           UUID NOT NULL PRIMARY KEY,
  activity_iri VARCHAR(255) NOT NULL UNIQUE,
  payload      JSON NOT NULL
)

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS attachment (
  id             UUID NOT NULL PRIMARY KEY,
  statement_id   UUID NOT NULL,
  attachment_sha VARCHAR(255) NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BINARY NOT NULL, -- TODO: Switch to BLOB?
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id)
)

-- :name create-statement-to-actor-table!
-- :command :execute
-- :doc Create the `statement_to_actor` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           UUID NOT NULL PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        ENUM('Actor', 'Object', 'Authority', 'Instructor', 'Team',
                    'SubActor', 'SubObject', 'SubInstructor', 'SubTeam')
               NOT NULL,
  actor_ifi    VARCHAR(255) NOT NULL,
  actor_type   ENUM('Agent', 'Group'),
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type)
)

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           UUID NOT NULL PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        ENUM('Object', 'Category', 'Grouping', 'Parent', 'Other',
                    'SubObject', 'SubCategory', 'SubGrouping', 'SubParent',
                    'SubOther')
               NOT NULL,
  activity_iri VARCHAR(255) NOT NULL,
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (activity_iri) REFERENCES activity(activity_iri)
)

-- :name create-statement-to-statement-table!
-- :command :execute
-- :doc Create the `statement_to_statement` link table, used for StatementRef associations, if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_statement (
  id            UUID NOT NULL PRIMARY KEY,
  ancestor_id   UUID NOT NULL,
  descendant_id UUID NOT NULL,
  FOREIGN KEY (ancestor_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (descendant_id) REFERENCES xapi_statement(statement_id)
)

/* Document Tables */

-- :name create-state-document-table!
-- :command :execute
-- :doc Create the `state_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS state_document (
  id             UUID NOT NULL PRIMARY KEY,
  state_id       VARCHAR(255) NOT NULL,
  activity_iri   VARCHAR(255) NOT NULL,
  agent_ifi      JSON NOT NULL,
  registration   UUID DEFAULT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BINARY NOT NULL
)

-- :name create-agent-profile-document-table!
-- :command :execute
-- :doc Create the `agent_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id             UUID NOT NULL PRIMARY KEY,
  profile_id     VARCHAR(255) NOT NULL,
  agent_ifi      VARCHAR(255) NOT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BINARY NOT NULL
)

-- :name create-activity-profile-document-table!
-- :command :execute
-- :doc Create the `activity_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id             UUID NOT NULL PRIMARY KEY,
  profile_id     VARCHAR(255) NOT NULL,
  activity_iri   VARCHAR(255) NOT NULL,
  last_modified  TIMESTAMP NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  content_length INTEGER NOT NULL,
  contents       BINARY NOT NULL
)

/* Admin Account Table */

-- :name create-admin-account-table!
-- :command :execute
-- :doc Create the `admin_account` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS admin_account (
  id       UUID NOT NULL PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  passhash VARCHAR(255) NOT NULL
)

/* Credential Tables */

-- :name create-credential-table!
-- :command :execute
-- :doc Create the `lrs_credential` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS lrs_credential (
  id         UUID NOT NULL PRIMARY KEY,
  api_key    VARCHAR(255) NOT NULL,
  secret_key VARCHAR(255) NOT NULL,
  account_id UUID NOT NULL,
  FOREIGN KEY (account_id) REFERENCES admin_account(id)
)

-- :name create-credential-to-scope-table!
-- :command :execute
-- :doc Create the `credential_to_scope` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS credential_to_scope (
  id         UUID NOT NULL PRIMARY KEY,
  api_key    VARCHAR(255) NOT NULL,
  secret_key VARCHAR(255) NOT NULL,
  scope      ENUM('statements/write',
                  'statements/read/mine', -- unimplemented
                  'statements/read',
                  'state',   -- unimplemented
                  'define',  -- unimplemented
                  'profile', -- unimplemented
                  'all/read',
                  'all'), -- enum is nullable
  FOREIGN KEY (api_key, secret_key) REFERENCES lrs_credential(api_key, secret_key)
)
