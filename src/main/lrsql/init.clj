(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [lrsql.backend.protocol :as bp]
            [lrsql.input.admin :as admin-input]
            [lrsql.input.auth  :as auth-input]
            [lrsql.ops.command.admin :as admin-cmd]
            [lrsql.ops.command.auth :as auth-cmd]
            [lrsql.ops.query.admin :as admin-q]
            [lrsql.ops.query.auth :as auth-q]))

(defn init-hugsql-adapter!
  "Initialize HugSql to use the next-jdbc adapter."
  []
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc)))

(defn init-backend!
  "Init the functionality of `backend`, including IO data conversion and
   setting up the DB tables and indexes."
  [backend tx]
  ;; Init IO data conversion
  (bp/-set-read! backend)
  (bp/-set-write! backend)
  ;; Init DDL
  (bp/-create-all! backend tx))

(defn insert-default-creds!
  "Seed the credential table with the default API key and secret, as well as
   the admin account table; the default API key and secret are set by the
   environmental variables. The scope of the default credentials would be
   hardcoded as \"all\". Does not seed the tables when the username
   or password is nil, or if the tables were already seeded."
  [backend tx ?username ?password]
  (when (and ?username ?password)
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
      ;; Don't insert if reconnecting to a previously-seeded DB
      (when-not (admin-q/query-admin backend tx admin-in)
        (admin-cmd/insert-admin! backend tx admin-in))
      (when-not (auth-q/query-credential-scopes* backend tx cred-in)
        (auth-cmd/insert-credential! backend tx cred-in)
        (auth-cmd/insert-credential-scopes! backend tx scope-in)))))
