(ns lrsql.ops.query.admin
  (:require [lrsql.functions  :as f]))

(defn query-admin
  [tx input]
  (if-some [{pass-hash :password_hash
             pass-salt :password_salt}
            (f/query-account tx input)]
    {:password-hash pass-hash
     :password-salt pass-salt}
    :lrsql.auth/missing-account-error))
