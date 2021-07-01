(ns lrsql.ops.command.admin
  (:require [lrsql.functions :as f]))

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table. Returns a map with `:result` either being the
   account ID on success or an error keyword on failure."
  [tx input]
  (if-not (f/query-account-exists tx (select-keys input [:username]))
    (do
      (f/insert-admin-account! tx input)
      {:result (:primary-key input)})
    {:result :lrsql.admin/existing-account-error}))

(defn delete-admin!
  "Delete the admin account and any associated credentials. Returns a map
   where `:result` is the account ID."
  [tx input]
  (f/delete-admin-credentials! tx input)
  (f/delete-admin-account! tx input)
  {:result (:account-id input)})
