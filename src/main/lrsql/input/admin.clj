(ns lrsql.input.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as adu]
            [lrsql.util :as u]))

(s/fdef insert-admin-input
  :args (s/cat :username ::ads/username :password ::ads/password)
  :ret ads/insert-admin-input-spec)

(defn insert-admin-input
  "Given `username` and `password`, construct the input param map for
   `insert-admin!`."
  [username password]
  {:primary-key (u/generate-squuid)
   :username    username
   :passhash    (adu/hash-password password)})

(s/fdef ensure-admin-oidc-input
  :args (s/cat :username ::ads/username
               :oidc-issuer :lrsql.spec.admin.input/oidc-issuer)
  :ret ads/ensure-admin-oidc-input-spec)

(defn ensure-admin-oidc-input
  "Given `username` and `oidc-issuer`, construct the input param map for
  `ensure-admin-oidc!`"
  [username oidc-issuer]
  {:username    username
   :oidc-issuer oidc-issuer})

(s/fdef insert-admin-oidc-input
  :args (s/cat :ensure-input ads/ensure-admin-oidc-input-spec)
  :ret ads/insert-admin-oidc-input-spec)

(defn insert-admin-oidc-input
  "Given an input from ensure-admin-oidc-input, add a primary key for use in
  `insert-admin-oidc!`"
  [ensure-input]
  (u/add-primary-key ensure-input))

(s/fdef get-account-input
  :args (s/cat :account-id ::ads/account-id)
  :ret ads/admin-id-input-spec)

(defn query-account-input
  "Given `account-id`, construct the input param map for `get-account`."
  [account-id]
  {:account-id account-id})

(s/fdef delete-admin-input
  :args (s/cat :account-id ::ads/account-id
               :oidc-enabled? :lrsql.spec.admin.input/oidc-enabled?)
  :ret ads/admin-id-input-spec)

(defn delete-admin-input
  "Given `account-id`, construct the input param map for `delete-admin!`."
  [account-id oidc-enabled?]
  {:account-id    account-id
   :oidc-enabled? oidc-enabled?})

(s/fdef query-admin-exists-input
  :args (s/cat :account-id ::ads/account-id)
  :ret ads/admin-id-input-spec)

(defn query-admin-exists-input
  "Given `account-id`, construct the input param map for `query-admin-exists.`"
  [account-id]
  {:account-id account-id})

(s/fdef query-validate-admin-input
  :args (s/cat :username ::ads/username :password ::ads/password)
  :ret ads/query-validate-admin-input-spec)

(defn query-validate-admin-input
  "Given `username` and `password`, construct the input param map for
   `query-validate-admin`."
  [username password]
  {:username username
   :password password})

(s/fdef query-validate-admin-by-id-input
  :args (s/cat :account-id ::ads/account-id :password ::ads/password)
  :ret ads/query-validate-admin-by-id-input-spec)

(defn query-validate-admin-by-id-input
  "Given `account-id` and `password`, construct the input param map for
   `query-validate-admin-by-id`."
  [account-id password]
  {:account-id account-id
   :password   password})

(s/fdef update-admin-password-input
  :args (s/cat :account-id ::ads/account-id :new-password ::ads/new-password)
  :ret ads/update-admin-password-input-spec)

(defn update-admin-password-input
  "Given `account-id` and `new-password`, construct the input
   param map for `update-admin-password`."
  [account-id new-password]
  {:account-id   account-id
   :new-passhash (adu/hash-password new-password)})
