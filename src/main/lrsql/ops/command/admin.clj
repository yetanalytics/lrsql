(ns lrsql.ops.command.admin
  (:require [lrsql.functions :as f]))

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table."
  [tx input]
  (let [uname  (:username input)
        exists (f/query-account-exists tx (select-keys input [:username]))]
    (if-not exists
      (f/insert-admin-account! tx input)
      (throw (ex-info (format "Username %s already exists!" uname)
                      {:type     ::existing-account
                       :username uname}) ))))

(defn delete-admin!
  [tx input]
  (f/delete-credentials-by-account! tx input)
  (f/delete-admin-account! tx input))
