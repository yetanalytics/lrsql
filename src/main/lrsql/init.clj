(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.functions :as f]
            [lrsql.interface :as inf]
            [lrsql.input.admin :as admin-input]
            [lrsql.input.auth  :as auth-input]
            [lrsql.ops.command.admin :as admin-cmd]
            [lrsql.ops.command.auth :as auth-cmd]))

(def ^{:private true :dynamic true} *aot-compiled* false)

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (when-not *aot-compiled*
    (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))))

(defn init-settable-params!
  "Set conversion functions for DB reading and writing depending on `db-type`."
  [db-type]
  (when-not *aot-compiled*
    (cond
      ;; H2
      (#{"h2" "h2:mem"} db-type)
      (do (inf/set-h2-read!)
          (inf/set-h2-write!))

      ;; SQLite
      (#{"sqlite"} db-type)
      (do (inf/set-sqlite-read!)
          (inf/set-sqlite-write!)))))

;; TODO: instead of using `db-type'`, we could rely entirely on the paths
;; in deps.edn
;; TODO: Adding the unbinding seems to massively slow down everything...
(defn init-hugsql-fns!
  "Define the HugSql functions defined in the `hugsql.functions` ns.
   The .sql files that HugSql reads from will depend on `db-type`."
  [db-type]
  (when-not *aot-compiled*
    ;; Hack the namespace binding or else the hugsql fn namespaces
    ;; will be whatever ns `init-hugsql-fns!` was called from.
    (let [db-type' (if (#{"h2:mem"} db-type) "h2" db-type)]
      (binding [*ns* (create-ns `lrsql.functions)]
        (let [fns (ns-publics *ns*)] ; map from fn syms to vars
          ;; Reset function namespace before redefining
          (dorun (map #(.unbindRoot (second %)) fns))
          ;; Define HugSql functions
          ;; Follow the CRUD acronym: Create, Read, Update, Delete
          (hugsql/def-db-fns (str db-type' "/ddl.sql"))
          (hugsql/def-db-fns (str db-type' "/insert.sql"))
          (hugsql/def-db-fns (str db-type' "/query.sql"))
          (hugsql/def-db-fns (str db-type' "/update.sql"))
          (hugsql/def-db-fns (str db-type' "/delete.sql"))
          ;; Define any remaining unbound fns as no-ops
          (dorun (map #(intern *ns* (first %) identity)
                      (filter #(-> % second bound? not) fns))))))))

(defn init-aot-compilation
  "Set the HugSql adapter, HugSql DB functions, and settable params during
   AOT compilation. If called, then `init-hugsql-adapter!`,
   `init-settable-params!`, and `init-hugsql-fns!` will do nothing
   on subsequent calls."
  [db-type]
  (init-hugsql-adapter!)
  (init-hugsql-fns! db-type)
  (init-settable-params! db-type)
  (alter-var-root #'*aot-compiled* (constantly true)))

(defn init-ddl!
  "Execute SQL commands to create tables if they do not exist."
  [conn]
  ;; Init properties
  (f/ensure-foreign-keys! conn)
  ;; Create tables
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
