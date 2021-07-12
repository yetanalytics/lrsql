(ns lrsql.input.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.auth :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-credential-input
  :args (s/cat :account-id ::ads/account-id
               :key-pair   as/key-pair-args-spec)
  :ret as/insert-cred-input-spec)

(defn insert-credential-input
  ([account-id key-pair]
   (assoc key-pair
          :primary-key (u/generate-squuid)
          :account-id  account-id))
  ([account-id api-key secret-key]
   {:primary-key (u/generate-squuid)
    :api-key     api-key
    :secret-key  secret-key
    :account-id  account-id}))

(s/fdef insert-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-args-spec
               :scopes   ::as/scopes)
  :ret as/insert-cred-scopes-input-spec)

(defn insert-credential-scopes-input
  ([key-pair scopes]
   (->> scopes
        (map (partial assoc key-pair :scope))
        (map (fn [skp] (assoc skp :primary-key (u/generate-squuid))))))
  ([api-key secret-key scopes]
   (let [key-pair {:api-key    api-key
                   :secret-key secret-key}]
     (insert-credential-scopes-input key-pair scopes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-credentials-input
  :args (s/cat :account-id ::ads/account-id
               :key-pair   as/key-pair-args-spec)
  :ret as/delete-cred-input-spec)

(defn delete-credentials-input
  ([account-id key-pair]
   (assoc key-pair :account-id account-id))
  ([account-id api-key secret-key]
   {:api-key    api-key
    :secret-key secret-key
    :account-id account-id}))

(s/fdef delete-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-args-spec
               :scopes   ::as/scopes)
  :ret as/delete-cred-scopes-input-spec)

(defn delete-credential-scopes-input
  ([key-pair scopes]
   (->> scopes
        (map (partial assoc key-pair :scope))))
  ([api-key secret-key scopes]
   (let [key-pair {:api-key    api-key
                   :secret-key secret-key}]
     (delete-credential-scopes-input key-pair scopes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-credentials-input
  :args (s/cat :account-id ::ads/account-id)
  :ret as/query-creds-input-spec)

(defn query-credentials-input
  [account-id]
  {:account-id account-id})

(s/fdef query-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-args-spec)
  :ret as/query-cred-scopes-input-spec)

(defn query-credential-scopes-input
  ([key-pair]
   key-pair)
  ([api-key secret-key]
   {:api-key    api-key
    :secret-key secret-key}))
