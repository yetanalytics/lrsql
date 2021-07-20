(ns lrsql.interface.protocol
  "Protocols that serve as a low-level interface for DB functions.")

(defprotocol DDLInterface
  (-create-all! [this tx]
    "Create all tables and indexes."))

(defprotocol StatementInterface
  ;; Commands
  (-insert-statement! [this tx input])
  (-insert-statement-to-statement! [this tx input])
  (-void-statement! [this tx input])
  ;; Queries
  (-query-statement [this tx input])
  (-query-statements [this tx input])
  (-query-statement-exists [this tx input])
  (-query-statement-descendants [this tx input]))

(defprotocol ActorInterface
  ;; Commands
  (-insert-actor! [this tx input])
  (-insert-statement-to-actor! [this tx input])
  (-update-actor! [this tx input])
  ;; Queries
  (-query-actor [this tx input]))

(defprotocol ActivityInterface
  ;; Commands
  (-insert-activity! [this tx input])
  (-insert-statement-to-activity! [this tx input])
  (-update-activity! [this tx input])
  ;; Queries
  (-query-activity [this tx input]))

(defprotocol AttachmentInterface
  ;; Commands
  (-insert-attachment! [this tx input])
  ;; Queries
  (-query-attachments [this tx input]))

(defprotocol StateDocumentInterface
  ;; Commands
  (-insert-state-document! [this tx input])
  (-update-state-document! [this tx input])
  (-delete-state-document! [this tx input])
  (-delete-state-documents! [this tx input])
  ;; Queries
  (-query-state-document [this tx input])
  (-query-state-document-ids [this tx input]))

(defprotocol AgentProfileDocumentInterface
  ;; Commands
  (-insert-agent-profile-document! [this tx input])
  (-update-agent-profile-document! [this tx input])
  (-delete-agent-profile-document! [this tx input])
  ;; Queries
  (-query-agent-profile-document [this tx input])
  (-query-agent-profile-document-ids [this tx input]))

(defprotocol ActivityProfileDocumentInterface
  ;; Commands
  (-insert-activity-profile-document! [this tx input])
  (-update-activity-profile-document! [this tx input])
  (-delete-activity-profile-document! [this tx input])
  ;; Queries
  (-query-activity-profile-document [this tx input])
  (-query-activity-profile-document-ids [this tx input]))

(defprotocol AdminAccountInterface
  ;; Commands
  (-insert-admin-account! [this tx input])
  (-delete-admin-account! [this tx input])
  ;; Queries
  (-query-account [this tx input])
  (-query-account-exists [this tx input]))

(defprotocol CredentialInterface
  ;; Commands
  (-insert-credential! [this tx input])
  (-insert-credential-scope! [this tx input])
  (-delete-credential! [this tx input])
  (-delete-credential-scope! [this tx input])
  ;; Queries
  (-query-credentials [this tx input])
  (-query-credential-exists [this tx input])
  (-query-credential-scopes [this tx input]))
