(ns lrsql.input.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.auth :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef credential-insert-input
  :args (s/cat :account-id ::ads/account-id :key-pair as/key-pair-spec)
  :ret as/cred-insert-spec)

(defn credential-insert-input
  [account-id key-pair]
  (assoc key-pair
         :primary-key (u/generate-squuid)
         :account-id account-id))

(s/fdef credential-scopes-insert-input
  :args (s/cat :key-pair as/key-pair-spec :scopes as/scopes-spec)
  :ret as/cred-scopes-insert-spec)

(defn credential-scopes-insert-input
  [key-pair scopes]
  (->> scopes
       (map (partial assoc key-pair :scope))
       (map (fn [skp] (assoc skp :primary-key (u/generate-squuid))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef credentials-delete-input
  :args (s/cat :account-id ::ads/account-id :key-pair as/key-pair-spec)
  :ret as/cred-delete-spec)

(defn credentials-delete-input
  [account-id key-pair]
  (assoc key-pair
         :account-id account-id))

(s/fdef credential-scopes-delete-input
  :args (s/cat :key-pair as/key-pair-spec :scopes as/scopes-spec)
  :ret as/cred-scopes-delete-spec)

(defn credential-scopes-delete-input
  [key-pair scopes]
  (->> scopes
       (map (partial assoc key-pair :scope))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef credentials-query-input
  :args (s/cat :account-id ::ads/account-id)
  :ret as/creds-query-spec)

(defn credentials-query-input
  [account-id]
  {:account-id account-id})

(s/fdef credential-query-scopes-input
  :args (s/cat :key-pair as/key-pair-spec)
  :ret as/cred-scopes-query-spec)

(defn credential-query-scopes-input
  [key-pair]
  key-pair)
