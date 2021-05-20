(ns lrsql.hugsql.functions
  "Namespace containing all HugSql functions.")

;; This ns is not strictly necessary, since `init-hugsql-fns!` will intern the
;; functions in this namespace anyways, but the functions are provided for
;; reference.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statements + Attachments
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Create tables
(declare create-statement-table!)
(declare create-agent-table!)
(declare create-activity-table!)
(declare create-attachment-table!)
(declare create-statement-to-agent-table!)
(declare create-statement-to-activity-table!)
(declare create-state-document-table!)
(declare create-agent-profile-document-table!)
(declare create-activity-profile-document-table!)

;; Insert
(declare insert-statement!)
(declare insert-agent!)
(declare insert-activity!)
(declare insert-attachment!)
(declare insert-statement-to-agent!)
(declare insert-statement-to-activity!)

;; Update
(declare void-statement!)

;; Query
(declare query-statements)
(declare query-agent)
(declare query-attachments)

;; Query existence
(declare query-agent-exists)
(declare query-activity-exists)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Documents
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Insert
(declare insert-state-document!)
(declare insert-agent-profile-document!)
(declare insert-activity-profile-document!)

;; Update
(declare update-state-document!)
(declare update-agent-profile-document!)
(declare update-activity-profile-document!)

;; Delete
(declare delete-state-document!)
(declare delete-state-documents!)
(declare delete-agent-profile-document!)
(declare delete-activity-profile-document!)

;; Query
(declare query-state-document)
(declare query-agent-profile-document)
(declare query-activity-profile-document)

;; Query IDs
(declare query-state-document-ids)
(declare query-agent-profile-document-ids)
(declare query-activity-profile-document-ids)
