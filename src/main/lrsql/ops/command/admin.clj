(ns lrsql.ops.command.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.input.admin :as admin-i]
            [lrsql.spec.common :as cs :refer [transaction?]]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.admin.jwt :as jwts]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/insert-admin-input-spec)
  :ret ads/insert-admin-ret-spec)

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table. Returns a map with `:result` either being the
   account ID on success or an error keyword on failure."
  [bk tx input]
  (if-not (bp/-query-account-exists bk tx (select-keys input [:username]))
    (do
      (bp/-insert-admin-account! bk tx input)
      {:result (:primary-key input)})
    {:result :lrsql.admin/existing-account-error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/delete-admin-input-spec)
  :ret ads/delete-admin-ret-spec)

(defn delete-admin!
  "Delete the admin account and any associated credentials. Returns a map
   where `:result` is the account ID.
   Will fail if the admin account does not exist.
   Will fail if the admin account is local and the only one and OIDC is not
   enabled."
  [bk tx {:keys [account-id
                 oidc-enabled?]
          :as   input}]
  {:result
   (if-let [{:keys [oidc_issuer]} (bp/-query-account-by-id bk tx input)]
     (if (or
          oidc-enabled? ;; Allow if OIDC is turned on
          oidc_issuer ;; OIDC accounts can always be deleted
          (< 1
             (:local_account_count
              (bp/-query-account-count-local bk tx)))) ;; more than one
       (do
         (bp/-delete-admin-account! bk tx input)
         account-id)
       :lrsql.admin/sole-admin-delete-error)
     :lrsql.admin/missing-account-error)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Update Password
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef update-admin-password!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/update-admin-password-input-spec)
  :ret ads/update-admin-password-ret-spec)

(defn update-admin-password!
  "Update the password of an admin account. Returns a map where `:result` is the
   account ID."
  [bk tx input]
  {:result
   (let [{:keys [id]} (bp/-query-account bk tx input)]
     (bp/-update-admin-password! bk tx input)
     id)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ensure Admin Account from OIDC
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef ensure-admin-oidc!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/ensure-admin-oidc-input-spec)
  :ret ads/ensure-admin-ret-spec)

(defn ensure-admin-oidc!
  "Create a new admin with OIDC issuer or verify issuer of an existing admin.
  Returns a map where `:result` is the account ID."
  [bk tx {:keys [username oidc-issuer]
          :as   ensure-input}]
  (if-let [{extant-issuer :oidc_issuer
            id            :id} (bp/-query-account-oidc
                                bk tx {:username username})]
    {:result
     (if (= oidc-issuer extant-issuer)
       id
       :lrsql.admin/oidc-issuer-mismatch-error)}
    (let [{:keys [primary-key]
           :as   insert-input} (admin-i/insert-admin-oidc-input
                                ensure-input)]
      (bp/-insert-admin-account-oidc! bk tx insert-input)
      {:result primary-key})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin JWTs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef purge-blocklist!
  :args (s/cat :bk jwts/admin-jwt-backend?
               :tx transaction?
               :input jwts/purge-blocklist-input-spec)
  :ret nil?)

(defn purge-blocklist!
  "Delete all JWTs from the blocklist that have expired, i.e. whose expirations
   are before the `:current-time` in `input`."
  [bk tx input]
  (bp/-delete-blocked-jwt-by-time! bk tx input)
  nil)

(s/fdef insert-blocked-jwt!
  :args (s/cat :bk jwts/admin-jwt-backend?
               :tx transaction?
               :input jwts/insert-blocked-jwt-input-spec)
  :ret (s/or :success jwts/blocked-jwt-op-result-spec
             :error (s/keys :req-un [::cs/error])))

(defn insert-blocked-jwt!
  "Insert a new JWT to the blocklist table."
  [bk tx input]
  (if (some? (bp/-query-blocked-jwt bk tx input))
    {:error (ex-info "Cannot have identical JWTs in the blocklist"
                     {:type ::jwt-conflict-error
                      :jwt  (:jwt input)})}
    (do
      (bp/-insert-blocked-jwt! bk tx input)
      {:result (:jwt input)})))

;; One-time JWTs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-one-time-jwt!
  :args (s/cat :bk jwts/admin-jwt-backend?
               :tx transaction?
               :input jwts/insert-one-time-jwt-input-spec)
  :ret (s/or :success jwts/blocked-jwt-op-result-spec
             :error (s/keys :req-un [::cs/error])))

(defn insert-one-time-jwt!
  "Insert a new one-time JWT to the blocklist table, with `:one-time-id`."
  [bk tx input]
  (if (some? (bp/-query-one-time-jwt bk tx input))
    {:error (ex-info "Cannot have identical one-time JWTs"
                     {:type ::jwt-conflict-error
                      :jwt  (:jwt input)})}
    (do
      (bp/-insert-one-time-jwt! bk tx input)
      {:result (:jwt input)})))

(s/fdef update-one-time-jwt!
  :args (s/cat :bk jwts/admin-jwt-backend?
               :tx transaction?
               :input jwts/update-one-time-jwt-input-spec)
  :ret (s/or :success jwts/blocked-jwt-op-result-spec
             :error (s/keys :req-un [::cs/error])))

(defn update-one-time-jwt!
  [bk tx input]
  (if (nil? (bp/-query-one-time-jwt bk tx input))
    {:error (ex-info "One-time JWT could not be found"
                     {:type ::jwt-not-found
                      :jwt  (:jwt input)})}
    (do
      (bp/-update-one-time-jwt! bk tx input)
      {:result (:jwt input)})))
