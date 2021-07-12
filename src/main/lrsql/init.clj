(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [clojure.string :as cstr]
            [next.jdbc.date-time :as next-dt]
            [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.functions :as f]
            [lrsql.input.admin :as admin-input]
            [lrsql.input.auth  :as auth-input]
            [lrsql.ops.command.admin :as admin-cmd]
            [lrsql.ops.command.auth :as auth-cmd]))

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))
  ;; Any time queried from a DB will now be read as an Instant.
  (next-dt/read-as-instant))

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
      (hugsql/def-db-fns (str db-type' "/create.sql"))
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
