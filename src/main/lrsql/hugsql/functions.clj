(ns lrsql.hugsql.functions
  "Namespace containing all HugSql functions.")

;; This ns is not strictly necessary, since `init-hugsql-fns!` will intern the
;; functions in this namespace anyways, but the functions are provided for
;; reference.

;; Create table
(declare create-statement-table!)
(declare create-agent-table!)
(declare create-activity-table!)
(declare create-attachment-table!)
(declare create-statement-to-agent-table!)
(declare create-statement-to-activity-table!)
(declare create-statement-to-attachment-table!)
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
(declare insert-statement-to-attachment!)

;; Update
(declare void-statement!)

;; Query
(declare query-statement)
(declare query-attachments)

;; Query existence
(declare query-agent-exists)
(declare query-activity-exists)
(declare query-attachment-exists)
