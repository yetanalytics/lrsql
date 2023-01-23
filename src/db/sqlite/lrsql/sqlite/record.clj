(ns lrsql.sqlite.record
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as cmp]
            [hugsql.core :as hug]
            [lrsql.backend.protocol :as bp]
            [lrsql.backend.data :as bd]
            [lrsql.init :refer [init-hugsql-adapter!]]
            [lrsql.sqlite.data :as sd]))

;; Init HugSql functions

(init-hugsql-adapter!)

(hug/def-db-fns "lrsql/sqlite/sql/ddl.sql")
(hug/def-db-fns "lrsql/sqlite/sql/insert.sql")
(hug/def-db-fns "lrsql/sqlite/sql/query.sql")
(hug/def-db-fns "lrsql/sqlite/sql/update.sql")
(hug/def-db-fns "lrsql/sqlite/sql/delete.sql")

;; Schema Update Helpers

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn- update-schema-simple!
  "Given a tx and a db-fn that updates schema to remove CHECK or FOREIGN KEY or NOT NULL constraints or to add, remove, or change default values on a column, wraps it with an update to the schema version and associated checks.

  See https://www.sqlite.org/lang_altertable.html Section 7 part 2"
  [tx db-fn]
  (enable-writable-schema! tx)
  (db-fn tx)
  (update-schema-version!
   tx
   (update (query-schema-version tx)
           :schema_version inc))
  (disable-writable-schema! tx)
  (run-integrity-check tx))

;; Define record

#_{:clj-kondo/ignore [:unresolved-symbol]} ; Shut up VSCode warnings
(defrecord SQLiteBackend []
  cmp/Lifecycle
  (start [this] this)
  (stop [this] this)

  bp/ConnectionOps
  (-conn-init-sql [_]
    ;; Extract the SQL command string from the sqlvec
    (first (ensure-foreign-keys-snip)))

  bp/BackendInit
  (-create-all! [_ tx]
    (create-statement-table! tx)
    (create-desc-id-index! tx)
    (create-verb-iri-index! tx)
    (create-registration-index! tx)

    (create-actor-table! tx)
    (create-activity-table! tx)

    (create-attachment-table! tx)
    (create-attachment-statement-id-index! tx)

    (create-statement-to-actor-table! tx)
    (create-statement-actor-statement-id-index! tx)
    (create-statement-actor-ifi-index! tx)

    (create-statement-to-activity-table! tx)
    (create-statement-activity-statement-id-index! tx)
    (create-statement-activity-iri-index! tx)

    (create-statement-to-statement-table! tx)
    (create-sts-ancestor-id-index! tx)
    (create-sts-descendant-id-index! tx)

    (create-state-document-table! tx)
    (create-agent-profile-document-table! tx)
    (create-activity-profile-document-table! tx)
    (create-admin-account-table! tx)
    (create-credential-table! tx)
    (create-credential-to-scope-table! tx))
  (-update-all! [_ tx]
    (when (= 1 (:notnull (query-admin-account-passhash-notnull tx)))
      (update-schema-simple! tx alter-admin-account-passhash-optional!))
    (when-not (some? (query-admin-account-oidc-issuer-exists tx))
      (alter-admin-account-add-openid-issuer! tx))
    (update-schema-simple! tx alter-credential-to-scope-scope-datatype!)
    (log/infof "sqlite schema_version: %d"
               (:schema_version (query-schema-version tx))))

  bp/BackendUtil
  (-txn-retry? [_ _ex]
    ;; No known retry cases for SQLite
    false)

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
  (-query-state-document-exists [_ tx input]
    (query-state-document-exists tx input))

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
  (-query-agent-profile-document-exists [_ tx input]
    (query-agent-profile-document-exists tx input))

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
  (-query-activity-profile-document-exists [_ tx input]
    (query-activity-profile-document-exists tx input))

  bp/AdminAccountBackend
  (-insert-admin-account! [_ tx input]
    (insert-admin-account! tx input))
  (-insert-admin-account-oidc! [_ tx input]
    (insert-admin-account-oidc! tx input))
  (-query-all-admin-accounts [_ tx]
    (query-all-accounts tx))
  (-delete-admin-account! [_ tx input]
    (delete-admin-account! tx input))
  (-query-account [_ tx input]
    (query-account tx input))
  (-query-account-oidc [_ tx input]
    (query-account-oidc tx input))
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
  (-query-credential-ids [_ tx input]
    (query-credential-ids tx input))
  (-query-credential-scopes [_ tx input]
    (query-credential-scopes tx input))

  bp/BackendIOSetter
  (-set-read! [_]
    (bd/set-read-time->instant!)
    (bd/set-read-bytes->json! ; SQLite returns JSON data as byte arrays even
     #{"payload"})            ; though the data type is "BLOB"
    (sd/set-read-str->uuid-or-inst!
     #{"id"
       "statement_id"
       "registration"
       "ancestor_id"
       "descendant_id"
       "cred_id"
       "account_id"}
     #{"last_modified"}))
  (-set-write! [_]
    (bd/set-write-json->bytes!)
    (sd/set-write-uuid->str!)
    (sd/set-write-inst->str!)
    (sd/set-write-bool->int!)))
