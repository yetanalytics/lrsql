(ns lrsql.ops.query.admin
  (:require [lrsql.functions :as f]
            [lrsql.util.admin :as au]))

(defn query-admin
  "Query an admin account with the given username and password. Returns
   a map containing `:account-id` and `:passhash` on success, or
   `:lrsql.admin/missing-account-error` on failure."
  [tx input]
  (if-some [{account-id :id
             passhash   :passhash}
            (f/query-account tx input)]
    {:account-id account-id
     :passhash   passhash}
    :lrsql.admin/missing-account-error))

(defn validate-admin
  "Validate the admin account info by querying the admin by `username`,
   then validating that `password` hashes into the same passhash stored
   in the account table. Returns the account ID on success or an error
   keyword on failure."
  [tx input]
  (let [res (query-admin tx input)]
    (cond
      (= :lrsql.admin/missing-account-error res)
      {:result res}
      (au/valid-password? (:password input) (:passhash res))
      {:result (:account-id res)}
      :else
      {:result :lrsql.admin/invalid-password-error})))
