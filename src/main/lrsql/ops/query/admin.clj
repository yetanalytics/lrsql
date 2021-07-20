(ns lrsql.ops.query.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as au]))

(defn- query-admin
  "Query an admin account with the given username and password. Returns
   a map containing `:account-id` and `:passhash` on success, or
   `:lrsql.admin/missing-account-error` on failure."
  [inf tx input]
  (if-some [{account-id :id
             passhash   :passhash}
            (ip/-query-account inf tx input)]
    {:account-id account-id
     :passhash   passhash}
    :lrsql.admin/missing-account-error))

(s/fdef query-validate-admin
  :args (s/cat :inf c/query-interface?
               :tx c/transaction?
               :input ads/query-validate-admin-input-spec)
  :ret ads/query-validate-admin-ret-spec)

(defn query-validate-admin
  "Queries the admin account table by `:username`, then validates that
   `:password` hashes into the same passhash stored in the account table.
   Returns the account ID on success or an error keyword on failure."
  [inf tx input]
  (let [res (query-admin inf tx input)]
    (cond
      (= :lrsql.admin/missing-account-error res)
      {:result res}
      (au/valid-password? (:password input) (:passhash res))
      {:result (:account-id res)}
      :else
      {:result :lrsql.admin/invalid-password-error})))
