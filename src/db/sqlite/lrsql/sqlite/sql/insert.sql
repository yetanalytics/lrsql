/* Statement + Attachment Insertion */

-- :name insert-statement!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement with statement resource params.
INSERT INTO xapi_statement (
  id, statement_id, registration, verb_iri, is_voided, payload, timestamp, stored
) VALUES (
  :primary-key, :statement-id, :registration, :verb-iri, :voided?, :payload, :timestamp, :stored
);

-- :name insert-actor!
-- :command :insert
-- :result :affected
-- :doc Insert a new actor with an IFI and optional name.
INSERT INTO actor (
  id, actor_ifi, actor_type, payload
) VALUES (
  :primary-key, :actor-ifi, :actor-type, :payload
);

-- :name insert-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity with an IRI.
INSERT INTO activity (
  id, activity_iri, payload
) VALUES (
  :primary-key, :activity-iri, :payload
);

-- :name insert-attachment!
-- :command :insert
-- :result :affected
-- :doc Insert a new attachment.
INSERT INTO attachment (
  id, statement_id, attachment_sha, content_type, content_length, contents
) VALUES (
  :primary-key, :statement-id, :attachment-sha, :content-type, :content-length, :contents
);

-- :name insert-statement-to-actor!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-actor relation.
INSERT INTO statement_to_actor (
  id, statement_id, usage, actor_ifi, actor_type
) VALUES (
  :primary-key, :statement-id, :usage, :actor-ifi, :actor-type
);

-- :name insert-statement-to-activity!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-activity relation.
INSERT INTO statement_to_activity (
  id, statement_id, usage, activity_iri
) VALUES (
  :primary-key, :statement-id, :usage, :activity-iri
);

-- :name insert-statement-to-statement!
-- :command :insert
-- :result :affected
-- :doc Insert a new statement-to-statement relation, where `:ancestor-id` is a previously-inserted statement.
INSERT INTO statement_to_statement (
  id, ancestor_id, descendant_id
) VALUES (
  :primary-key, :ancestor-id, :descendant-id
);

/* Document Insertion */

-- :name insert-state-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new state document using resource params.
INSERT INTO state_document (
  id, state_id, activity_iri, agent_ifi, registration,
  last_modified, content_type, content_length, contents
) VALUES (
  :primary-key, :state-id, :activity-iri, :agent-ifi, :registration,
  :last-modified, :content-type, :content-length, :contents
)

-- :name insert-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new agent profile document using resource params.
INSERT INTO agent_profile_document (
  id, profile_id, agent_ifi,
  last_modified, content_type, content_length, contents
) VALUES (
  :primary-key, :profile-id, :agent-ifi,
  :last-modified, :content-type, :content-length, :contents
)

-- :name insert-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Insert a new activity profile document using resource params.
INSERT INTO activity_profile_document (
  id, profile_id, activity_iri,
  last_modified, content_type, content_length, contents
) VALUES (
  :primary-key, :profile-id, :activity-iri,
  :last-modified, :content-type, :content-length, :contents
)

/* Accounts */

-- :name insert-admin-account!
-- :command :insert
-- :result :affected
INSERT INTO admin_account (
  id, username, passhash
) VALUES (
  :primary-key, :username, :passhash
)

-- :name insert-admin-account-oidc!
-- :command :insert
-- :doc Insert a new admin account to shadow an OIDC user.
INSERT INTO admin_account (
  id, username, oidc_issuer
) VALUES (
  :primary-key, :username, :oidc-issuer
)

/* Credentials */

-- :name insert-credential!
-- :command :insert
-- :result :affected
-- :doc Given API keys and `:account-id`, insert the credentials into the credential table.
INSERT INTO lrs_credential (
  id, api_key, secret_key, account_id
) VALUES (
  :primary-key, :api-key, :secret-key, :account-id
)

-- :name insert-credential-scope!
-- :command :insert
-- :result :affected
-- :doc Given API keys and a `:scope` value, insert the cred-scope relation into the `credential_to_scope` link table.
INSERT INTO credential_to_scope (
  id, api_key, secret_key, scope
) VALUES (
  :primary-key, :api-key, :secret-key, :scope
)
