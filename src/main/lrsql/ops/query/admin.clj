(ns lrsql.ops.query.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as au]))

(s/fdef query-validate-admin
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/query-validate-admin-input-spec)
  :ret ads/query-admin-ret-spec)

(defn query-admin
  "Query an admin account with the given username and password. Returns
   a map containing `:account-id` and `:passhash` on success, or nil on
   failure."
  [bk tx input]
  (when-some [{account-id :id
             passhash   :passhash}
            (bp/-query-account bk tx input)]
    {:account-id account-id
     :passhash   passhash}))

(s/fdef query-validate-admin
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/query-validate-admin-input-spec)
  :ret ads/query-validate-admin-ret-spec)

(defn query-validate-admin
  "Queries the admin account table by `:username`, then validates that
   `:password` hashes into the same passhash stored in the account table.
   Returns the account ID on success or an error keyword on failure."
  [bk tx input]
  (let [res (query-admin bk tx input)]
    (cond
      (nil? res)
      {:result :lrsql.admin/missing-account-error}
      (au/valid-password? (:password input) (:passhash res))
      {:result (:account-id res)}
      :else
      {:result :lrsql.admin/invalid-password-error})))

(s/fdef query-admin-accounts
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?)
  :ret ads/query-admin-accounts-ret-spec)

(defn query-admin-accounts
  "Query all admin accounts. Returns a vec of maps containing `:account-id`
  and `:username` on success, or empty vec on failure."
  [bk tx]
  (mapv (fn [{account-id :id username :username}]
          {:account-id account-id
           :username username})
        (bp/-query-admin-accounts bk tx)))
