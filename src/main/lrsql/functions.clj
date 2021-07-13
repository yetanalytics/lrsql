(ns lrsql.functions
  "Namespace containing all HugSql functions.")

;; This ns is not strictly necessary, since `init-hugsql-fns!` will intern the
;; functions in this namespace anyways, but the functions are provided for
;; reference.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create Tables
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement + Attachment Tables
(declare create-statement-table!)
(declare create-actor-table!)
(declare create-activity-table!)
(declare create-attachment-table!)
(declare create-statement-to-actor-table!)
(declare create-statement-to-activity-table!)
(declare create-statement-to-statement-table!)

;; Document Tables
(declare create-state-document-table!)
(declare create-agent-profile-document-table!)
(declare create-activity-profile-document-table!)

;; Account + Credential Tables
(declare create-admin-account-table!)
(declare create-credential-table!)
(declare create-credential-to-scope-table!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insert
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statements + Statement Objects
(declare insert-statement!)
(declare insert-actor!)
(declare insert-activity!)
(declare insert-attachment!)
(declare insert-statement-to-actor!)
(declare insert-statement-to-activity!)
(declare insert-statement-to-statement!)

;; Documents
(declare insert-state-document!)
(declare insert-agent-profile-document!)
(declare insert-activity-profile-document!)

;; Credentials + Admin Accounts
(declare insert-admin-account!)
(declare insert-credential!)
(declare insert-credential-scope!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Actors + Activities
(declare update-actor!)
(declare update-activity!)

;; Verbs
(declare void-statement!)

;; Documents
(declare update-state-document!)
(declare update-agent-profile-document!)
(declare update-activity-profile-document!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Documents
(declare delete-state-document!)
(declare delete-state-documents!)
(declare delete-agent-profile-document!)
(declare delete-activity-profile-document!)

;; Credentials + Admin Accounts
(declare delete-credential-scope!)
(declare delete-credential!)
(declare delete-admin-credentials!)
(declare delete-admin-account!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statements
(declare query-statement)
(declare query-statements)
(declare query-statement-exists)

;; Statement Objects
(declare query-actor)
(declare query-activity)
(declare query-attachments)

;; Statement References
(declare query-statement-descendants)

;; Documents
(declare query-state-document)
(declare query-agent-profile-document)
(declare query-activity-profile-document)

;; Document Existence
(declare query-state-document-exists)
(declare query-agent-profile-document-exists)
(declare query-activity-profile-document-exists)

;; Document IDs
(declare query-state-document-ids)
(declare query-agent-profile-document-ids)
(declare query-activity-profile-document-ids)

;; Admin Accounts
(declare query-account)
(declare query-account-exists)

;; Credentials
(declare query-credentials)
(declare query-credential-exists)
(declare query-credential-scopes)
