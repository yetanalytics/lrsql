-- :name create-statement-table!
-- :command :execute
-- :doc Create the `statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id               UUID NOT NULL PRIMARY KEY,
  statement_id     UUID NOT NULL UNIQUE,
  statement_ref_id UUID,
  stored           TIMESTAMP NOT NULL,
  registration     UUID,
  verb_iri         VARCHAR(255) NOT NULL,
  is_voided        BOOLEAN DEFAULT FALSE NOT NULL,
  payload          JSON NOT NULL
)

-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet
CREATE TABLE IF NOT EXISTS actor (
  id         UUID NOT NULL PRIMARY KEY,
  actor_ifi  VARCHAR(255) NOT NULL UNIQUE,
  actor_type ENUM('Agent', 'Group') NOT NULL,
  payload    JSON NOT NULL
)

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet
CREATE TABLE IF NOT EXISTS activity (
  id           UUID NOT NULL PRIMARY KEY,
  activity_iri VARCHAR(255) NOT NULL UNIQUE,
  payload      JSON NOT NULL
)

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet
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
-- :doc Create the `statement_to_actor` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           UUID NOT NULL PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        ENUM('Actor', 'Object', 'Authority', 'Instructor', 'Team',
                    'SubActor', 'SubObject', 'SubInstructor', 'SubTeam')
               NOT NULL,
  actor_ifi    VARCHAR(255) NOT NULL,
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (actor_ifi) REFERENCES actor(actor_ifi)
)

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it does not exist yet.
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
