-- :name create-statement-table
-- :command :execute
-- :result :raw
-- :doc Create the statement table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id               UUID NOT NULL PRIMARY KEY,
  statement_id     UUID NOT NULL UNIQUE,
  sub_statement_id UUID,
  statement_ref_id UUID,
  created          TIMESTAMP WITH TIME ZONE NOT NULL, -- aka `timestamp`
  stored           TIMESTAMP WITH TIME ZONE NOT NULL,
  registration     UUID,
  verb_iri         VARCHAR(255) NOT NULL,
  is_voided        BOOLEAN DEFAULT FALSE NOT NULL,
  payload          JSON NOT NULL
)

-- :name create-agent-table
-- :command :execute
-- :result :raw
-- :doc Create the agent table if it does not exist yet
CREATE TABLE IF NOT EXISTS agent (
  id                  UUID NOT NULL PRIMARY KEY,
  agent_name          VARCHAR(255),
  agent_ifi           JSON NOT NULL,
  is_identified_group BOOLEAN DEFAULT FALSE NOT NULL
)

-- :name create-activity-table
-- :command :execute
-- :result :raw
-- :doc Create the activity table if it does not exist yet
CREATE TABLE IF NOT EXISTS activity (
  id           UUID NOT NULL PRIMARY KEY,
  activity_iri VARCHAR(255) NOT NULL UNIQUE,
  payload      JSON NOT NULL
)

-- :name create-attachment-table
-- :command :execute
-- :result :raw
-- :doc Create the attachment table if it does not exist yet
CREATE TABLE IF NOT EXISTS attachment (
  id             UUID NOT NULL PRIMARY KEY,
  attachment_sha VARCHAR(255) UNIQUE NOT NULL,
  content_type   VARCHAR(255) NOT NULL,
  file_url       VARCHAR(255) NOT NULL, -- Either external or points to LRS location
  payload        BINARY -- Switch to BLOB?
)

-- :name create-statement-to-agent-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_agent link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_agent (
  id             UUID NOT NULL PRIMARY KEY,
  statement_id   UUID NOT NULL,
  usage          ENUM('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL,
  agent_ifi      JSON NOT NULL
)

-- :name create-statement-to-activity-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_activity link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           UUID NOT NULL PRIMARY KEY,
  statement_id UUID NOT NULL,
  usage        ENUM('Object', 'Category', 'Grouping', 'Parent', 'Other') NOT NULL,
  activity_iri VARCHAR(255) NOT NULL
)

-- :name create-statement-to-attachment-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_attachment link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_attachment (
  id              UUID NOT NULL PRIMARY KEY,
  statement_id    UUID NOT NULL,
  attachment_sha  VARCHAR(255) NOT NULL
)

-- :name create-state-document-table
-- :command :execute
-- :result :raw
-- :doc Create the state_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS state_document (
  id            UUID NOT NULL PRIMARY KEY,
  state_id      VARCHAR(255) NOT NULL,
  activity_id   VARCHAR(255) NOT NULL,
  agent_id      JSON NOT NULL,
  registration  UUID,
  last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
  document      BINARY
)

-- :name create-agent-profile-document-table
-- :command :execute
-- :result :raw
-- :doc Create the agent_profile_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id            UUID NOT NULL PRIMARY KEY,
  state_id      VARCHAR(255) NOT NULL,
  agent_id      JSON NOT NULL,
  last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
  document      BINARY
)

-- :name create-activity-profile-document-table
-- :command :execute
-- :result :raw
-- :doc Create the activity_profile_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id            UUID NOT NULL PRIMARY KEY,
  profile_id    VARCHAR(255) NOT NULL,
  activity_id   VARCHAR(255) NOT NULL,
  last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
  document      BINARY
)