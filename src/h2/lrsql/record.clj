(ns lrsql.record
  (:require [hugsql.core :as hugsql]
            [lrsql.protocol :as p]))

;; Init HugSql functions

(hugsql/def-db-fns "lrsql/sql/ddl.sql")
(hugsql/def-db-fns "lrsql/sql/insert.sql")
(hugsql/def-db-fns "lrsql/sql/query.sql")
(hugsql/def-db-fns "lrsql/sql/update.sql")
(hugsql/def-db-fns "lrsql/sql/delete.sql")

 ;; Define record

#_{:clj-kondo/ignore [:unresolved-symbol]} ; Shut up VSCode warnings
(defrecord LrsqlDBInterface []
  p/LrsqlDDL
  (-create-all! [_ tx]
    (create-statement-table! tx)
    (create-actor-table! tx)
    (create-activity-table! tx)
    (create-attachment-table! tx)
    (create-statement-to-actor-table!)
    (create-statement-to-activity-table! tx)
    (create-statement-to-statement-table! tx)
    (create-state-document-table! tx)
    (create-agent-profile-document-table! tx)
    (create-activity-profile-document-table! tx)
    (create-admin-account-table! tx)
    (create-credential-table! tx)
    (create-credential-to-scope-table! tx))
  (-drop-all! [_ _tx]
    ;; TODO
    nil)

  p/LrsqlInsert
  ;; Statements + Statement Objects
  (-insert-statement! [_ tx input]
    (insert-statement! tx input))
  (-insert-actor! [_ tx input]
    (insert-actor! tx input))
  (-insert-activity! [_ tx input]
    (insert-activity! tx input))
  (-insert-attachment! [_ tx input]
    (insert-attachment! tx input))
  (-insert-statement-to-actor! [_ tx input]
    (insert-statement-to-actor! tx input))
  (-insert-statement-to-activity [_ tx input]
    (insert-statement-to-activity! tx input))
  (-insert-statement-to-statement [_ tx input]
    (insert-statement-to-statement! tx input))
  ;; Documents
  ;; Need to query first since H2 doesn't have dupe checking on insert
  (-insert-state-document! [_ tx input]
    (when-not (query-state-document-exists tx input)
      (insert-state-document! tx input)))
  (-insert-agent-profile-document! [_ tx input]
    (when-not (query-agent-profile-document-exists tx input)
      (insert-agent-profile-document! tx input)))
  (-insert-activity-profile-document! [_ tx input]
    (when-not (query-activity-profile-document-exists tx input)
      (insert-activity-profile-document! tx input)))
  ;; Credentials + Admin Accounts
  (-insert-admin-account! [_ tx input]
    (insert-admin-account! tx input))
  (-insert-credential! [_ tx input]
    (insert-credential! tx input))
  (-insert-credential-scope! [_ tx input]
    (insert-credential-scope! tx input))

  p/LrsqlUpdate
  ;; Actors + Activities
  (-update-actor! [_ tx input]
    (update-actor! tx input))
  (-update-activity! [_ tx input]
    (update-activity! tx input))
  ;; Verbs
  (-void-statement! [_ tx input]
    (void-statement! tx input))
  ;; Documents
  (-update-state-document! [_ tx input]
    (update-state-document! tx input))
  (-update-agent-profile-document! [_ tx input]
    (update-agent-profile-document! tx input))
  (-update-activity-profile-document! [_ tx input]
    (update-activity-profile-document! tx input))

  p/LrsqlDelete
  ;; Documents
  (-delete-state-document! [_ tx input]
    (delete-state-document! tx input))
  (-delete-state-documents! [_ tx input]
    (delete-state-documents! tx input))
  (-delete-agent-profile-document! [_ tx input]
    (delete-agent-profile-document! tx input))
  (-delete-activity-profile-document! [_ tx input]
    (delete-activity-profile-document! tx input))
  ;; Credentials + Admin Accounts
  (-delete-admin-account! [_ tx input]
    (delete-admin-account! tx input))
  (-delete-credential! [_ tx input]
    (delete-credential! tx input))
  (-delete-credential-scopes! [_ tx input]
    (delete-credential-scopes! tx input))

  p/LrsqlQuery
  ;; Statement
  (-query-statement [_ tx input]
    (query-statement tx input))
  (-query-statements [_ tx input]
    (query-statements tx input))
  (-query-statement-exists [_ tx input]
    (query-statement-exists tx input))
  ;; Statement Objects
  (-query-actor [_ tx input]
    (query-actor tx input))
  (-query-activity [_ tx input]
    (query-activity tx input))
  (-query-attachments [_ tx input]
    (query-attachments tx input))
  ;; Statement References
  (-query-statement-descendants [_ tx input]
    (query-statement-descendants tx input))
  ;; Documents
  (-query-state-document [_ tx input]
    (query-state-document tx input))
  (-query-agent-profile-document [_ tx input]
    (query-agent-profile-document tx input))
  (-query-activity-profile-document [_ tx input]
    (query-activity-profile-document tx input)))
