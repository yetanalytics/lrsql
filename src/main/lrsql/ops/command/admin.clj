(ns lrsql.ops.command.admin
  (:require [lrsql.functions :as f]))

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table."
  [tx input]
  (if-not (f/query-account-exists tx input)
    (do
      (f/insert-admin-account! tx input)
      :lrsql.admin/success)
    :lrsql.admin/existing-account-error))

(defn delete-admin!
  "Delete the admin account and any associated credentials."
  [tx input]
  (f/delete-admin-credentials! tx input tx input)
  (f/delete-admin-account! tx input))
