(ns lrsql.ops.command.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.admin :as ads]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-admin!
  :args (s/cat :inf (s/and c/query-interface? c/insert-interface?)
               :tx c/transaction?
               :input ads/insert-admin-input-spec)
  :ret ads/insert-admin-ret-spec)

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table. Returns a map with `:result` either being the
   account ID on success or an error keyword on failure."
  [inf tx input]
  (if-not (ip/-query-account-exists inf tx (select-keys input [:username]))
    (do
      (ip/-insert-admin-account! inf tx input)
      {:result (:primary-key input)})
    {:result :lrsql.admin/existing-account-error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-admin!
  :args (s/cat :inf c/delete-interface?
               :tx c/transaction?
               :input ads/delete-admin-input-spec)
  :ret ads/delete-admin-ret-spec)

(defn delete-admin!
  "Delete the admin account and any associated credentials. Returns a map
   where `:result` is the account ID."
  [inf tx input]
  (ip/-delete-admin-account! inf tx input)
  {:result (:account-id input)})
