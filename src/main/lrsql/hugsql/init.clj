(ns lrsql.hugsql.init
  "Initialize HugSql functions and state."
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.hugsql.functions :as f]))

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc)))

(defn init-hugsql-fns!
  "Define the HugSql functions defined in the `hugsql.functions` ns.
   The .sql files that HugSql reads from will depend on `db-type`."
  [db-type]
  ;; Hack the namespace binding or else the hugsql fn namespaces
  ;; will be whatever ns `init-hugsql-fns!` was called from.
  (binding [*ns* `lrsql.hugsql.functions]
    (hugsql/def-db-fns (str db-type "/create.sql"))
    (hugsql/def-db-fns (str db-type "/insert.sql"))
    (hugsql/def-db-fns (str db-type "/query.sql"))
    ;; For snippets
    (hugsql/def-sqlvec-fns (str db-type "/create.sql"))
    (hugsql/def-sqlvec-fns (str db-type "/insert.sql"))
    (hugsql/def-sqlvec-fns (str db-type "/query.sql"))))

(defn create-tables!
  "Execute SQL commands to create tables if they do not exist."
  [conn]
  (f/create-statement-table conn)
  (f/create-agent-table conn)
  (f/create-activity-table conn)
  (f/create-attachment-table conn)
  (f/create-statement-to-agent-table conn)
  (f/create-statement-to-activity-table conn)
  (f/create-statement-to-attachment-table conn)
  (f/create-state-document-table conn)
  (f/create-agent-profile-document-table conn)
  (f/create-activity-profile-document-table conn))
