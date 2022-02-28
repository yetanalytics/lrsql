(ns lrsql.ops.command.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.input.admin :as admin-i]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.admin :as ads]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :admin-input ads/insert-admin-input-spec)
  :ret ads/insert-admin-ret-spec)

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table. Returns a map with `:result` either being the
   account ID on success or an error keyword on failure."
  [bk tx admin-input]
  (if-not (bp/-query-account-exists bk tx (select-keys admin-input [:username]))
    (let [account-input (admin-i/insert-admin-account-input admin-input)]
      (bp/-insert-admin-account! bk tx account-input)
      {:result (:primary-key account-input)})
    {:result :lrsql.admin/existing-account-error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/admin-id-input-spec)
  :ret ads/delete-admin-ret-spec)

(defn delete-admin!
  "Delete the admin account and any associated credentials. Returns a map
   where `:result` is the account ID."
  [bk tx input]
  (if (bp/-query-account-exists bk tx input)
    (do
      (bp/-delete-admin-account! bk tx input)
      {:result (:account-id input)})
    {:result :lrsql.admin/missing-account-error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ensure Admin Account from OIDC
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef ensure-admin-oidc!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :ensure-input ads/ensure-admin-oidc-input-spec)
  :ret ads/ensure-admin-ret-spec)

(defn ensure-admin-oidc!
  "Create a new admin with OIDC issuer or verify issuer of an existing admin.
  Returns a map where `:result` is the account ID."
  [bk tx {:keys [username oidc-issuer]
          :as   ensure-input}]
  (if-let [{extant-issuer :oidc_issuer ;; TODO: is not coerced to kebab?
            id            :id} (bp/-query-account-oidc
                                bk tx {:username username})]
    {:result
     (if (= oidc-issuer extant-issuer)
       id
       :lrsql.admin/oidc-issuer-mismatch-error)}
    (let [{:keys [primary-key]
           :as   insert-input} (admin-i/insert-admin-oidc-input
                                ensure-input)]
      (do
        (bp/-insert-admin-account-oidc! bk tx insert-input)
        {:result primary-key}))))
