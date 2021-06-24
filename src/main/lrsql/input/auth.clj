(ns lrsql.input.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.auth :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-credential-input
  :args (s/cat :account-id ::ads/account-id :key-pair as/key-pair-spec)
  :ret as/cred-insert-spec)

(defn insert-credential-input
  [account-id key-pair]
  (assoc key-pair
         :primary-key (u/generate-squuid)
         :account-id account-id))

(s/fdef insert-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-spec :scopes as/scopes-spec)
  :ret as/cred-scope-insert-spec)

(defn insert-credential-scopes-input
  [key-pair scopes]
  (->> scopes
       (map (partial assoc key-pair))
       (map #(assoc % :primary-key (u/generate-squuid)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-credentials-input
  :args (s/cat :account-id ::ads/account-id :key-pair as/key-pair-spec)
  :ret as/cred-scope-delete-spec)

(defn delete-credentials-input
  [account-id key-pair]
  (assoc key-pair
         :account-id account-id))

(s/fdef delete-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-spec :scopes as/scopes-spec)
  :ret as/cred-scope-delete-spec)

(defn delete-credential-scopes-input
  ([key-pair]
   key-pair)
  ([key-pair scopes]
   (->> scopes
        (map (partial assoc key-pair)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-credentials-input
  :args (s/cat :account-id ::ads/account-id)
  :ret as/creds-query-spec)

(defn query-credentials-input
  [account-id]
  {:account-id account-id})

(s/fdef query-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-spec)
  :ret as/cred-scopes-query-spec)

(defn query-credential-scopes-input
  [key-pair]
  key-pair)
