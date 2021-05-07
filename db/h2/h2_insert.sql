-- :name insert-statement
-- :command :insert
-- :result :affected
-- :doc Insert a new statement.
INSERT INTO statement
SET Id = :statement-pk,
SET StatementId = :statement-id,
SET SubStatementId = :?sub-statement-id,
SET StatementRefId = :?statement-ref-id,
SET Timestamp = :timestamp,
SET Stored = :stored,
SET Registration = :?registration,
SET VerbIri = :verb-iri,
SET IsVoided = :voided?,
SET Data = :data

-- :name insert-activity
-- :command :insert
-- :result :affected
-- :doc Insert a new activity.
INSERT INTO activity
SET Id = :activity-pk,
SET ActivityIri = :activity-iri,
SET Data = :data

-- :name insert-agent
-- :command :insert
-- :result :affected
-- :doc Insert a new agent.
INSERT INTO agent
SET Id = :agent-pk,
SET Name = :name,
SET Mbox = :mbox,
SET MboxSha1Sum = :mbox-sha1sum,
SET OpenId = :openid,
SET AccountName = :account-name,
SET AccountHomepage = :account-homepage,
SET IsIdentifiedGroup = :identified-group?

-- :name insert-attachment,
-- :command :insert,
-- :result :affected
-- :doc Insert a new attachment
INSERT INTO attachment
SET Id = :attachment-pk,
SET Sha2 = :sha2,
SET ContentType = :content-type,
SET FileUrl = :file-url,
SET Data = :data

-- :name insert-statement-to-activity
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-activity relation.
INSERT INTO statement_to_activity
SET Id = :primary-key,
SET StatementId = :statement-id,
SET Usage = :usage,
SET ActivityIri = :activity-iri

-- :name insert-statement-to-agent
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-agent relation.
INSERT INTO statement_to_agent
SET Id = :primary-key,
SET StatementId = :statement-id,
SET Usage = :usage,
SET AgentIfi = :agent-ifi,
SET AgentIfiType = :agent-ifi-type

-- :name insert-statement-to-attachment
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-attachment relation.
INSERT INTO statement_to_attachment
SET Id = :primary-key,
SET StatementId = :statement-id,
SET AttachmentSha = :sha2
