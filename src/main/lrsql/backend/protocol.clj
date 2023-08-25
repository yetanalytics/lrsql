(ns lrsql.backend.protocol
  "Protocols that serve as a low-level backend for DB functions.")

(defprotocol ConnectionOps
  (-conn-init-sql [this]
    "Return SQL commands strings to operate when a Hikari connection starts.
     Returns `nil` if no commands are available."))

(defprotocol BackendInit
  (-create-all! [this tx]
    "Create the tables, indexes, etc. specified by the DDL.")
  (-update-all! [this tx]
    "Update the tables, indexes, etc. specified by the DDL."))

(defprotocol BackendIOSetter
  (-set-read! [this]
    "Set data conversion behavior when reading from the backend.")
  (-set-write! [this]
    "Set data conversion behavior when writing from the backend."))

(defprotocol BackendUtil
  (-txn-retry? [this ex]
    "Determine if an exception should be retried in a txn"))

(defprotocol StatementBackend
  ;; Commands
  (-insert-statement! [this tx input])
  (-insert-statement-to-statement! [this tx input])
  (-void-statement! [this tx input])
  ;; Queries
  (-query-statement [this tx input])
  (-query-statements [this tx input])
  (-query-statement-exists [this tx input])
  (-query-statement-descendants [this tx input])
  (-query-statement-ids-by-actor [this tx input]))

(defprotocol ActorBackend
  ;; Commands
  (-insert-actor! [this tx input])
  (-insert-statement-to-actor! [this tx input])
  (-update-actor! [this tx input])
  (-wipe-actor! [this tx input])
  ;; Queries
  (-query-actor [this tx input]))

(defprotocol ActivityBackend
  ;; Commands
  (-insert-activity! [this tx input])
  (-insert-statement-to-activity! [this tx input])
  (-update-activity! [this tx input])
  ;; Queries
  (-query-activity [this tx input]))

(defprotocol AttachmentBackend
  ;; Commands
  (-insert-attachment! [this tx input])
  ;; Queries
  (-query-attachments [this tx input]))

(defprotocol StateDocumentBackend
  ;; Commands
  (-insert-state-document! [this tx input])
  (-update-state-document! [this tx input])
  (-delete-state-document! [this tx input])
  (-delete-state-documents! [this tx input])
  ;; Queries
  (-query-state-document [this tx input])
  (-query-state-document-ids [this tx input])
  (-query-state-document-exists [this tx input]))

(defprotocol AgentProfileDocumentBackend
  ;; Commands
  (-insert-agent-profile-document! [this tx input])
  (-update-agent-profile-document! [this tx input])
  (-delete-agent-profile-document! [this tx input])
  ;; Queries
  (-query-agent-profile-document [this tx input])
  (-query-agent-profile-document-ids [this tx input])
  (-query-agent-profile-document-exists [this tx input]))

(defprotocol ActivityProfileDocumentBackend
  ;; Commands
  (-insert-activity-profile-document! [this tx input])
  (-update-activity-profile-document! [this tx input])
  (-delete-activity-profile-document! [this tx input])
  ;; Queries
  (-query-activity-profile-document [this tx input])
  (-query-activity-profile-document-ids [this tx input])
  (-query-activity-profile-document-exists [this tx input]))

(defprotocol AdminAccountBackend
  ;; Commands
  (-insert-admin-account! [this tx input])
  (-insert-admin-account-oidc! [this tx input])
  (-delete-admin-account! [this tx input])
  (-update-admin-password! [this tx input])
  ;; Queries
  (-query-account [this tx input])
  (-query-account-oidc [this tx input])
  (-query-account-by-id [this tx input])
  (-query-account-exists [this tx input])
  (-query-all-admin-accounts [this tx])
  (-query-account-count-local [this tx]))

(defprotocol CredentialBackend
  ;; Commands
  (-insert-credential! [this tx input])
  (-insert-credential-scope! [this tx input])
  (-delete-credential! [this tx input])
  (-delete-credential-scope! [this tx input])
  ;; Queries
  (-query-credentials [this tx input])
  (-query-credential-ids [this tx input])
  (-query-credential-scopes [this tx input]))

(defprotocol AdminStatusBackend
  ;; Queries
  (-query-statement-count [this tx])
  (-query-actor-count [this tx])
  (-query-last-statement-stored [this tx])
  (-query-platform-frequency [this tx])
  (-query-timeline [this tx input]))
