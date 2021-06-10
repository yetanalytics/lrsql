(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [clojure.string :as cstr]
            [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.functions :as f]))

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc)))

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
  (f/create-statement-id-index! conn)
  (f/create-statement-verb-index! conn)
  (f/create-statement-registration-index! conn)
  (f/create-actor-table! conn)
  (f/create-activity-table! conn)
  (f/create-attachment-table! conn)
  (f/create-statement-to-actor-table! conn)
  (f/create-statement-to-activity-table! conn)
  (f/create-statement-to-statement-table! conn)
  (f/create-state-document-table! conn)
  (f/create-agent-profile-document-table! conn)
  (f/create-activity-profile-document-table! conn))
