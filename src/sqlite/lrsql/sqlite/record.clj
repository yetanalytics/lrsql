(ns lrsql.sqlite.record
  (:require [com.stuartsierra.component :as cmp]
            [lrsql.backend.protocol :as bp]
            [lrsql.util :as u]))

;; Init HugSql functions

(u/def-hugsql-db-fns "lrsql/sqlite/sql/ddl.sql")
(u/def-hugsql-db-fns "lrsql/sqlite/sql/insert.sql")
(u/def-hugsql-db-fns "lrsql/sqlite/sql/query.sql")
(u/def-hugsql-db-fns "lrsql/sqlite/sql/update.sql")
(u/def-hugsql-db-fns "lrsql/sqlite/sql/delete.sql")

;; Define record

#_{:clj-kondo/ignore [:unresolved-symbol]} ; Shut up VSCode warnings
(defrecord SQLiteBackend []
  cmp/Lifecycle
  (start [this] this)
  (stop [this] this)

  bp/BackendInit
  (-create-all! [_ tx]
    (ensure-foreign-keys! tx)
    (create-statement-table! tx)
    (create-desc-id-index! tx)
    (create-verb-iri-index! tx)
    (create-registration-index! tx)
    (create-actor-table! tx)
    (create-activity-table! tx)
    (create-attachment-table! tx)
    (create-statement-to-actor-table! tx)
    (create-statement-to-activity-table! tx)
    (create-statement-to-statement-table! tx)
    (create-state-document-table! tx)
    (create-agent-profile-document-table! tx)
    (create-activity-profile-document-table! tx)
    (create-admin-account-table! tx)
    (create-credential-table! tx)
    (create-credential-to-scope-table! tx))

  bp/StatementBackend
  (-insert-statement! [_ tx input]
    (insert-statement! tx input))
  (-insert-statement-to-statement! [_ tx input]
    (insert-statement-to-statement! tx input))
  (-void-statement! [_ tx input]
    (void-statement! tx input))
  (-query-statement [_ tx input]
    (query-statement tx input))
  (-query-statements [_ tx input]
    (query-statements tx input))
  (-query-statement-exists [_ tx input]
    (query-statement-exists tx input))
  (-query-statement-descendants [_ tx input]
    (query-statement-descendants tx input))

  bp/ActorBackend
  (-insert-actor! [_ tx input]
    (insert-actor! tx input))
  (-insert-statement-to-actor! [_ tx input]
    (insert-statement-to-actor! tx input))
  (-update-actor! [_ tx input]
    (update-actor! tx input))
  (-query-actor [_ tx input]
    (query-actor tx input))

  bp/ActivityBackend
  (-insert-activity! [_ tx input]
    (insert-activity! tx input))
  (-insert-statement-to-activity! [_ tx input]
    (insert-statement-to-activity! tx input))
  (-update-activity! [_ tx input]
    (update-activity! tx input))
  (-query-activity [_ tx input]
    (query-activity tx input))

  bp/AttachmentBackend
  (-insert-attachment! [_ tx input]
    (insert-attachment! tx input))
  (-query-attachments [_ tx input]
    (query-attachments tx input))

  bp/StateDocumentBackend
  (-insert-state-document! [_ tx input]
    (insert-state-document! tx input))
  (-update-state-document! [_ tx input]
    (update-state-document! tx input))
  (-delete-state-document! [_ tx input]
    (delete-state-document! tx input))
  (-delete-state-documents! [_ tx input]
    (delete-state-documents! tx input))
  (-query-state-document [_ tx input]
    (query-state-document tx input))
  (-query-state-document-ids [_ tx input]
    (query-state-document-ids tx input))

  bp/AgentProfileDocumentBackend
  (-insert-agent-profile-document! [_ tx input]
    (insert-agent-profile-document! tx input))
  (-update-agent-profile-document! [_ tx input]
    (update-agent-profile-document! tx input))
  (-delete-agent-profile-document! [_ tx input]
    (delete-agent-profile-document! tx input))
  (-query-agent-profile-document [_ tx input]
    (query-agent-profile-document tx input))
  (-query-agent-profile-document-ids [_ tx input]
    (query-agent-profile-document-ids tx input))

  bp/ActivityProfileDocumentBackend
  (-insert-activity-profile-document! [_ tx input]
    (insert-activity-profile-document! tx input))
  (-update-activity-profile-document! [_ tx input]
    (update-activity-profile-document! tx input))
  (-delete-activity-profile-document! [_ tx input]
    (delete-activity-profile-document! tx input))
  (-query-activity-profile-document [_ tx input]
    (query-activity-profile-document tx input))
  (-query-activity-profile-document-ids [_ tx input]
    (query-activity-profile-document-ids tx input))

  bp/AdminAccountBackend
  (-insert-admin-account! [_ tx input]
    (insert-admin-account! tx input))
  (-delete-admin-account! [_ tx input]
    (delete-admin-account! tx input))
  (-query-account [_ tx input]
    (query-account tx input))
  (-query-account-exists [_ tx input]
    (query-account-exists tx input))

  bp/CredentialBackend
  (-insert-credential! [_ tx input]
    (insert-credential! tx input))
  (-insert-credential-scope! [_ tx input]
    (insert-credential-scope! tx input))
  (-delete-credential! [_ tx input]
    (delete-credential! tx input))
  (-delete-credential-scope! [_ tx input]
    (delete-credential-scope! tx input))
  (-query-credentials [_ tx input]
    (query-credentials tx input))
  (-query-credential-exists [_ tx input]
    (query-credential-exists tx input))
  (-query-credential-scopes [_ tx input]
    (query-credential-scopes tx input))

  ;; TODO
  bp/BackendIOSetter
  (-set-read! [_]
    nil)
  (-set-write! [_]
    nil))
