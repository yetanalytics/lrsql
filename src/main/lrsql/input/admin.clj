(ns lrsql.input.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as as]
            [lrsql.util.admin :as ua]
            [lrsql.util :as u]))

(s/fdef admin-insert-input
  :args (s/cat :username ::as/username :password ::as/password)
  :ret as/admin-insert-spec)

(defn admin-insert-input
  [username password]
  {:primary-key (u/generate-squuid)
   :username    username
   :passhash    (ua/hash-password password)})

(s/fdef admin-validate-input
  :args (s/cat :username ::as/username :password ::as/password)
  :ret as/admin-validate-spec)

(defn admin-validate-input
  [username password]
  {:username username
   :password password})

(s/fdef admin-delete-input
  :args (s/cat :account-id ::as/account-id)
  :ret as/admin-delete-spec)

(defn admin-delete-input
  [account-id]
  {:account-id account-id})
