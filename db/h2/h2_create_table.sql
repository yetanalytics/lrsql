-- :name create-statement-table
-- :command :execute
-- :result :raw
-- :doc Create the statement table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement (
  Id             UUID NOT NULL PRIMARY KEY,
  StatementId    UUID NOT NULL UNIQUE,
  SubStatementId UUID,
  StatementRefId UUID,
  Timestamp      TIMESTAMP WITH TIME ZONE NOT NULL,
  Stored         TIMESTAMP WITH TIME ZONE NOT NULL,
  Registration   UUID,
  VerbIri        VARCHAR(255) NOT NULL,
  IsVoided       BOOLEAN DEFAULT FALSE NOT NULL,
  Data           JSON NOT NULL
)

-- :name create-statement-to-activity-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_activity link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  Id          UUID NOT NULL PRIMARY KEY,
  StatementId UUID NOT NULL,
  Usage       ENUM('Object', 'Category', 'Grouping', 'Parent', 'Other') NOT NULL,
  ActivityIri VARCHAR(255) NOT NULL
)

-- :name create-statement-to-agent-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_agent link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_agent (
  Id           UUID NOT NULL PRIMARY KEY,
  StatementId  UUID NOT NULL,
  Usage        ENUM('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL,
  AgentIfi     VARCHAR(255) NOT NULL,
  AgentIfiType ENUM('Mbox', 'MboxSHA1Sum', 'OpenID', 'Account') NOT NULL
)

-- :name create-statement-to-agent-table
-- :command :execute
-- :result :raw
-- :doc Create the statement_to_attachment link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS statement_to_attachment (
  Id             UUID NOT NULL PRIMARY KEY,
  StatementId    UUID NOT NULL,
  AttachmentSha: VARCHAR(255) NOT NULL
)

-- :name create-activity-table
-- :command :execute
-- :result :raw
-- :doc Create the activity table if it does not exist yet
CREATE TABLE IF NOT EXISTS activity (
  Id          UUID NOT NULL PRIMARY KEY,
  ActivityIri VARCHAR(255) NOT NULL UNIQUE,
  Data        JSON NOT NULL
)

-- :name create-agent-table
-- :command :execute
-- :result :raw
-- :doc Create the agent table if it does not exist yet
CREATE TABLE IF NOT EXISTS agent (
  Id                UUID NOT NULL PRIMARY KEY,
  Name              VARCHAR(255),
  Mbox              VARCHAR(255),
  MboxSha1Sum       VARCHAR(255),
  OpenId            VARCHAR(255),
  AccountName       VARCHAR(255),
  AccountHomepage   VARCHAR(255),
  IsIdentifiedGroup BOOLEAN DEFAULT FALSE NOT NULL
)

-- :name create-attachment-table
-- :command :execute
-- :result :raw
-- :doc Create the attachment table if it does not exist yet
CREATE TABLE IF NOT EXISTS attachment (
  Id          UUID NOT NULL PRIMARY KEY,
  Sha2        VARCHAR(255) UNIQUE NOT NULL,
  ContentType VARCHAR(255) NOT NULL,
  FileUrl     VARCHAR(255) NOT NULL, -- Either external or points to LRS location
  Data        BINARY -- Switch to BLOB?
)

-- :name create-state-document-table
-- :command :execute
-- :result :raw
-- :doc Create the state_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS state_document (
  Id           UUID NOT NULL PRIMARY KEY,
  StateId      VARCHAR(255) NOT NULL,
  ActivityId   VARCHAR(255) NOT NULL,
  AgentId      JSON NOT NULL,
  Registration UUID,
  LastModified TIMESTAMP WITH TIME ZONE NOT NULL,
  Document     BINARY
)

-- :name create-agent-profile-document-table
-- :command :execute
-- :result :raw
-- :doc Create the agent_profile_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS agent_profile_document (
  Id           UUID NOT NULL PRIMARY KEY,
  ProfileId    VARCHAR(255) NOT NULL,
  AgentId      JSON NOT NULL,
  LastModified TIMESTAMP WITH TIME ZONE NOT NULL,
  Document     BINARY
)

-- :name create-activity-document-table
-- :command :execute
-- :result :raw
-- :doc Create the activity_profile_document table if it does not exist yet
CREATE TABLE IF NOT EXISTS activity_profile_document (
  Id           UUID NOT NULL PRIMARY KEY,
  ProfileId    VARCHAR(255) NOT NULL,
  ActivityId   VARCHAR(255) NOT NULL,
  LastModified TIMESTAMP WITH TIME ZONE NOT NULL,
  Document     BINARY
)