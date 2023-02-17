(ns lrsql.ops.query.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.admin.status :as ss]
            [lrsql.util :as u]
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

(defn query-admin-exists
  "Query whether an admin account with the given ID exists. Returns true
   if it does exists, false otherwise."
  [bk tx input]
  (boolean (bp/-query-account-exists bk tx input)))

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
      (and (:passhash res) (au/valid-password? (:password input) (:passhash res)))
      {:result (:account-id res)}
      :else
      {:result :lrsql.admin/invalid-password-error})))

(s/fdef query-all-admin-accounts
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?)
  :ret ads/query-all-admin-accounts-ret-spec)

(defn query-all-admin-accounts
  "Query all admin accounts. Returns a vec of maps containing `:account-id`
  and `:username` on success, or empty vec on failure."
  [bk tx]
  (mapv (fn [{account-id :id username :username}]
          {:account-id account-id
           :username username})
        (bp/-query-all-admin-accounts bk tx)))

(s/fdef query-status
  :args (s/cat :bk ss/admin-status-backend?
               :tx transaction?
               :input ss/query-status-input-spec)
  :ret ss/query-status-ret-spec)

(defn query-status
  "Get status information about the LRS including statement counts and other
  metric information."
  [bk tx {:keys [include] :as params}]
  (cond-> {}
    (include "statement-count")
    (assoc :statement-count
           (:scount (bp/-query-statement-count bk tx)))

    (include "actor-count")
    (assoc :actor-count
           (:acount (bp/-query-actor-count bk tx)))

    (include "last-statement-stored")
    (assoc :last-statement-stored
           (:lstored (bp/-query-last-statement-stored bk tx)))

    (include "platform-frequency")
    (assoc :platform-frequency
           (reduce
            (fn [m {:keys [platform scount]}]
              (assoc m platform scount))
            {}
            (bp/-query-platform-frequency bk tx)))

    (include "timeline")
    (assoc :timeline
           (mapv
            (fn [{:keys [stored scount]}]
              {:stored (u/pad-time-str stored)
               :count scount})
            (bp/-query-timeline bk tx (:timeline params))))))
