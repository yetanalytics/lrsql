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

(s/fdef admin-query-input
  :args (s/cat :username ::as/username)
  :ret as/admin-query-spec)

(defn admin-query-input
  [username]
  {:username username})

(s/fdef admin-delete-input
  :args (s/cat :account-id ::as/account-id)
  :ret as/admin-delete-spec)

(defn admin-delete-input
  [account-id]
  {:account-id account-id})
