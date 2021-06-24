(ns lrsql.input.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.string     :as cstr]
            [lrsql.spec.auth    :as as]
            [lrsql.util         :as u]))

(defn insert-credential-input
  [account-id key-pair]
  (assoc key-pair
         :primary-key (u/generate-squuid)
         :account-id account-id))

(defn delete-credentials-input
  [account-id]
  {:account-id account-id})

(defn update-credential-scopes-input
  [key-pair scopes]
  (->> scopes
       (map (partial assoc key-pair))
       (map #(assoc % :primary-key (u/generate-squuid)))))

(defn query-credentials-input
  [account-id]
  {:account-id account-id})

(defn query-credential-scopes-input
  [key-pair]
  key-pair)
