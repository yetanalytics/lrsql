(ns lrsql.init
  "Initialize HugSql functions and state."
  (:require [clojure.tools.logging :as log]
            [hugsql.core :as hugsql]
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
  (log/debug "Ensuring Tables...")
  (bp/-create-all! backend tx)
  (log/debug "Running Migrations...")
  (bp/-update-all! backend tx)
  (log/debug "SQL backend initialization complete!"))

(defn insert-default-creds!
  "Seed the credential table with the default API key and secret, as well as
   the admin account table; the default API key and secret are set by the
   environmental variables. The scope of the default credentials would be
   hardcoded as \"all\". Does not seed the tables when the username
   or password is nil, or if the tables were already seeded."
  [backend tx ?username ?password ?api-key ?secret-key]
  ;; Seed Admin Account
  (when (and ?username ?password)
    (let [admin-in (admin-input/insert-admin-input
                    ?username
                    ?password)]
      ;; Don't insert account or creds if reconnecting to a DB previously
      ;; seeded with an account
      (when-not (admin-q/query-admin backend tx admin-in)
        ;; Insert admin account
        (admin-cmd/insert-admin! backend tx admin-in)
        ;; Seed Credentials
        (when (and ?api-key ?secret-key)
          (let [key-pair {:api-key    ?api-key
                          :secret-key ?secret-key}
                acc-id   (:primary-key admin-in)
                cred-in  (auth-input/insert-credential-input
                          acc-id nil key-pair)
                scope-in (auth-input/insert-credential-scopes-input
                          key-pair #{"all"})
                seed-in  (auth-input/update-credential-is-seed-input
                          key-pair true)]
            ;; Don't insert creds if reconnecting to a DB previously seeded
            ;; with a cred
            (when-not (auth-q/query-credential-scopes* backend tx cred-in)
              (auth-cmd/insert-credential! backend tx cred-in)
              (auth-cmd/insert-credential-scopes! backend tx scope-in)
              (auth-cmd/update-credential-is-seed! backend tx seed-in))))))))
