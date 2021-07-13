(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [clojure.string :as cstr]
            [xapi-schema.spec.regex :as xsr]
            [next.jdbc.date-time :as next-dt]
            [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.functions :as f]
            [lrsql.input.admin :as admin-input]
            [lrsql.input.auth  :as auth-input]
            [lrsql.ops.command.admin :as admin-cmd]
            [lrsql.ops.command.auth :as auth-cmd]
            [lrsql.util :as u])
  (:import [java.sql PreparedStatement ResultSetMetaData]))

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc)))

(defmacro byte-array-class
  []
  (class (byte-array [])))

(defn init-settable-params!
  [db-type]
  ;; Any time queried from a DB will now be read as an Instant.
  (next-dt/read-as-instant)

  ;; JSON data will always be stored as a byte array
  ;; TODO: Update for postgres
  (extend-protocol SettableParameter
    clojure.lang.IPersistentMap
    (set-parameter [^clojure.lang.IPersistentMap m ^PreparedStatement s ^long i]
      (.setBytes s i (u/write-json m))))
  (extend-protocol ReadableColumn
    (byte-array-class)
    (read-column-by-label [^"[B" b label]
      (if (#{"payload"} label) (u/parse-json b) b))
    (read-column-by-index [^"[B" b rsmeta i]
      (if (#{"payload"} (.getColumnLabel ^ResultSetMetaData rsmeta i))
        (u/parse-json b)
        b)))

  ;; SQLite does not support UUIDs, timestamps, or even booleans natively
  (when (#{"sqlite"} db-type)
    (extend-protocol SettableParameter
      java.util.UUID
      (set-parameter [^java.util.UUID u ^PreparedStatement s ^long i]
        (.setString s i (u/uuid->str u)))
      java.time.Instant
      (set-parameter [^java.time.Instant ts ^PreparedStatement s ^long i]
        (.setString s i (u/time->str ts)))
      java.lang.Boolean
      (set-parameter [^java.lang.Boolean b ^PreparedStatement s ^long i]
        (.setInt s i (if b 1 0))))
    (extend-protocol ReadableColumn
      java.sql.Blob
      (read-column-by-label [^java.sql.Blob b])
      java.lang.String
      (read-column-by-label [^java.lang.String s _]
        (if (re-matches xsr/UuidRegEx s) (u/str->uuid s) s))
      (read-column-by-index [^java.lang.String s _ _]
        (if (re-matches xsr/UuidRegEx s) (u/str->uuid s) s)))))

;; TODO: instead of using `db-type'`, we could rely entirely on the paths
;; in deps.edn
(defn init-hugsql-fns!
  "Define the HugSql functions defined in the `hugsql.functions` ns.
   The .sql files that HugSql reads from will depend on `db-type`."
  [db-type]
  ;; Hack the namespace binding or else the hugsql fn namespaces
  ;; will be whatever ns `init-hugsql-fns!` was called from.
  (let [db-type' (cstr/replace db-type #":.*" "")] ; h2:mem -> h2
    (binding [*ns* (create-ns `lrsql.functions)]
      ;; Follow the CRUD acronym: Create, Read, Update, Delete
      (hugsql/def-db-fns (str db-type' "/ddl.sql"))
      (hugsql/def-db-fns (str db-type' "/insert.sql"))
      (hugsql/def-db-fns (str db-type' "/query.sql"))
      (hugsql/def-db-fns (str db-type' "/update.sql"))
      (hugsql/def-db-fns (str db-type' "/delete.sql")))))

(defn create-tables!
  "Execute SQL commands to create tables if they do not exist."
  [conn]
  (f/create-statement-table! conn)
  (f/create-actor-table! conn)
  (f/create-activity-table! conn)
  (f/create-attachment-table! conn)
  (f/create-statement-to-actor-table! conn)
  (f/create-statement-to-activity-table! conn)
  (f/create-statement-to-statement-table! conn)
  (f/create-state-document-table! conn)
  (f/create-agent-profile-document-table! conn)
  (f/create-activity-profile-document-table! conn)
  (f/create-admin-account-table! conn)
  (f/create-credential-table! conn)
  (f/create-credential-to-scope-table! conn))

(defn insert-default-creds!
  "Seed the credential table with the default API key and secret, which are
   set by the environmental variables. The scope of the default credentials
   would be hardcoded as \"all\". Does not seed the table when the username
   or password is nil."
  [tx ?username ?password]
  (when (and ?username ?password)
    ;; TODO: Default admin also from config vars?
    (let [admin-in (admin-input/insert-admin-input
                    ?username
                    ?password)
          key-pair {:api-key    ?username
                    :secret-key ?password}
          cred-in  (auth-input/insert-credential-input
                    (:primary-key admin-in)
                    key-pair)
          scope-in (auth-input/insert-credential-scopes-input
                    key-pair
                    #{"all"})]
      (admin-cmd/insert-admin! tx admin-in)
      (auth-cmd/insert-credential! tx cred-in)
      (auth-cmd/insert-credential-scopes! tx scope-in))))
