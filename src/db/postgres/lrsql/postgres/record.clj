(ns lrsql.postgres.record
  (:require [com.stuartsierra.component :as cmp]
            [hugsql.core :as hug]
            [next.jdbc :as jdbc]
            [lrsql.backend.data :as bd]
            [lrsql.backend.protocol :as bp]
            [lrsql.init :refer [init-hugsql-adapter!]]
            [lrsql.postgres.data :as pd]
            [clojure.string :refer [includes?]])
  (:import [org.postgresql.util PSQLException]))

;; Init HugSql functions

(init-hugsql-adapter!)

(hug/def-db-fns "lrsql/postgres/sql/ddl.sql")
(hug/def-db-fns "lrsql/postgres/sql/insert.sql")
(hug/def-db-fns "lrsql/postgres/sql/query.sql")
(hug/def-db-fns "lrsql/postgres/sql/update.sql")
(hug/def-db-fns "lrsql/postgres/sql/delete.sql")

(hug/def-sqlvec-fns "lrsql/postgres/sql/query.sql")

;; Define record
#_{:clj-kondo/ignore [:unresolved-symbol]} ; Shut up VSCode warnings
(defrecord PostgresBackend [tuning]
  cmp/Lifecycle
  (start [this] this)
  (stop [this] this)

  bp/ConnectionOps
  (-conn-init-sql [_]
    nil)

  bp/BackendInit
  (-create-all! [_ tx]
    ;; Enums
    (create-actor-type-enum! tx)
    (create-actor-usage-enum! tx)
    (create-activity-usage-enum! tx)
    (create-scope-enum! tx)
    ;; Tables
    (create-statement-table! tx)
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
  (-update-all! [_ tx]
    (alter-admin-account-passhash-optional! tx)
    (alter-admin-account-add-openid-issuer! tx)
    (when-not (some? (query-scope-enum-updated tx))
      (alter-scope-enum-type! tx))
    (when-not (some? (query-xapi-statement-timestamp-exists tx))
      (alter-xapi-statement-add-timestamp! tx)
      (migrate-xapi-statement-timestamps! tx))
    (when-not (some? (query-xapi-statement-stored-exists tx))
      (alter-xapi-statement-add-stored! tx)
      (migrate-xapi-statement-stored-times! tx))
    (when-not (some? (query-state-document-last-modified-is-timestamptz tx))
      (migrate-state-document-last-modified! tx pd/local-tz-input)
      (migrate-activity-profile-document-last-modified! tx pd/local-tz-input)
      (migrate-agent-profile-document-last-modified! tx pd/local-tz-input))
    (create-reaction-table! tx)
    (when-not (some? (query-xapi-statement-reaction-id-exists tx))
      (xapi-statement-add-reaction-id! tx))
    (when-not (some? (query-xapi-statement-trigger-id-exists tx))
      (xapi-statement-add-trigger-id! tx))
    (if (-> tuning :config :enable-jsonb)
      (migrate-to-jsonb! tx)
      (migrate-to-json! tx))
    (when (nil? (check-statement-to-actor-cascading-delete tx))
      (add-statement-to-actor-cascading-delete! tx))
    (when (some? (query-varchar-exists tx))
      (convert-varchars-to-text! tx))
    (create-blocked-jwt-table! tx)
    (alter-blocked-jwt-add-one-time-id! tx)
    (alter-lrs-credential-add-label! tx)
    (alter-lrs-credential-add-is-seed! tx))

  bp/BackendUtil
  (-txn-retry? [_ ex]
    ;; only retry PGExceptions with a specified phrase
    (and (instance? PSQLException ex)
         (let [msg (.getMessage ^PSQLException ex)]
           (or (includes? msg "ERROR: deadlock detected")
               (includes? msg "ERROR: could not serialize access due to concurrent update")
               (includes? msg "ERROR: could not serialize access due to read/write dependencies among transactions")))))

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
  (-query-statements-lazy [_ tx input]
    (let [sqlvec (query-statements-sqlvec input)]
      (jdbc/plan tx sqlvec {:fetch-size  4000
                            :concurrency :read-only
                            :cursors     :close
                            :result-type :forward-only})))

  bp/ActorBackend
  (-insert-actor! [_ tx input]
    (insert-actor! tx input))
  (-insert-statement-to-actor! [_ tx input]
    (insert-statement-to-actor! tx input))
  (-update-actor! [_ tx input]
    (update-actor! tx input))
  (-delete-actor! [_ tx input]
    (delete-actor-and-dependents! tx input))
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
  (-update-admin-password! [_ tx input]
    (update-admin-password! tx input))
  (-query-account [_ tx input]
    (query-account tx input))
  (-query-account-oidc [_ tx input]
    (query-account-oidc tx input))
  (-query-account-by-id [_ tx input]
    (query-account-by-id tx input))
  (-query-account-exists [_ tx input]
    (query-account-exists tx input))
  (-query-account-count-local [_ tx]
    (query-account-count-local tx))

  bp/JWTBlocklistBackend
  (-insert-blocked-jwt! [_ tx input]
    (insert-blocked-jwt! tx input))
  (-insert-one-time-jwt! [_ tx input]
    (insert-one-time-jwt! tx input))
  (-update-one-time-jwt! [_ tx input]
    (update-one-time-jwt! tx input))
  (-delete-blocked-jwt-by-time! [_ tx input]
    (delete-blocked-jwt-by-time! tx input))
  (-query-blocked-jwt [_ tx input]
    (query-blocked-jwt-exists tx input))
  (-query-one-time-jwt [_ tx input]
    (query-one-time-jwt-exists tx input))

  bp/CredentialBackend
  (-insert-credential! [_ tx input]
    (insert-credential! tx input))
  (-insert-credential-scope! [_ tx input]
    (insert-credential-scope! tx input))
  (-update-credential-label! [_ tx input]
    (update-credential-label! tx input))
  (-update-credential-is-seed! [_ tx input]
    (update-credential-is-seed! tx input))
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
    (pd/set-read-pgobject->json!
     #{"ruleset"
       "error"}))
  (-set-write! [_]
    ;; next.jdbc automatically sets the reading of Instants as java.sql.Dates
    (pd/set-write-json->pgobject! (if (-> tuning :config :enable-jsonb)
                                    "jsonb" "json")))

  bp/AdminStatusBackend
  (-query-statement-count [_ tx]
    (query-statement-count tx))
  (-query-actor-count [_ tx]
    (query-actor-count tx))
  (-query-last-statement-stored [_ tx]
    (query-last-statement-stored tx))
  (-query-platform-frequency [_ tx]
    (query-platform-frequency tx))
  (-query-timeline [_ tx input]
    (query-timeline tx input))

  bp/ReactionBackend
  (-insert-reaction! [_ tx params]
    (try
      (insert-reaction! tx params)
      (catch PSQLException ex
        (if (= "23505" (.getSQLState ex))
          :lrsql.reaction/title-conflict-error
          (throw ex)))))
  (-update-reaction! [_ tx params]
    (try
      (update-reaction! tx params)
      (catch PSQLException ex
        (if (= "23505" (.getSQLState ex))
          :lrsql.reaction/title-conflict-error
          (throw ex)))))
  (-delete-reaction! [_ tx params]
    (delete-reaction! tx params))
  (-error-reaction! [_ tx params]
    (error-reaction! tx params))
  (-snip-json-extract [_ {:keys [datatype] :as params}]
    (if (-> tuning :config :enable-jsonb)
      (snip-jsonb-extract (assoc params :type (pd/type->pg-type datatype)))
      (snip-json-extract (assoc params :type (pd/type->pg-type datatype)))))
  (-snip-val [_ params]
    (snip-val params))
  (-snip-col [_ params]
    (snip-col params))
  (-snip-clause [_ params]
    (snip-clause params))
  (-snip-and [_ params]
    (snip-and params))
  (-snip-or [_ params]
    (snip-or params))
  (-snip-not [_ params]
    (snip-not params))
  (-snip-contains [_ {:keys [datatype] :as params}]
    (if (-> tuning :config :enable-jsonb)
      (snip-contains-jsonb (assoc params :type (pd/type->pg-type datatype)))
      (snip-contains-json (assoc params :type (pd/type->pg-type datatype)))))
  (-snip-query-reaction [_ params]
    (snip-query-reaction params))
  (-query-reaction [_ tx params]
    (query-reaction tx params))
  (-query-active-reactions [_ tx]
    (query-active-reactions tx))
  (-query-all-reactions [_ tx]
    (query-all-reactions tx))
  (-query-reaction-history [_ tx params]
    (query-reaction-history tx params)))
