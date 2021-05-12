(ns lrsql.hugsql.functions)

;; Namespace containing all HugSql functions.
;; This is not strictly necessary, since `init-hugsql-fns!` will intern the
;; functions in this namespace anyways, but the functions are provided
;; for reference.

(declare create-statement-table)
(declare create-agent-table)
(declare create-activity-table)
(declare create-attachment-table)
(declare create-statement-to-agent-table)
(declare create-statement-to-activity-table)
(declare create-statement-to-attachment-table)
(declare create-state-document-table)
(declare create-agent-profile-document-table)
(declare create-activity-profile-document-table)

(declare insert-statement)
(declare insert-agent)
(declare insert-activity)
(declare insert-attachment)
(declare insert-statement-to-agent)
(declare insert-statement-to-activity)
(declare insert-statement-to-attachment)

(declare query-statement)

(declare statement-id-snip)
(declare is-voided-snip)
(declare verb-iri-snip)
(declare registration-snip)
(declare timestamp-since-snip)
(declare timestamp-until-snip)
(declare statement-to-agent-join-snip)
(declare statement-to-activity-join-snip)
(declare limit-snip)
