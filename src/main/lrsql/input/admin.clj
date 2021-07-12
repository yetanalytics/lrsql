(ns lrsql.input.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin :as as]
            [lrsql.util.admin :as ua]
            [lrsql.util :as u]))

(s/fdef insert-admin-input
  :args (s/cat :username ::as/username :password ::as/password)
  :ret as/insert-admin-input-spec)

(defn insert-admin-input
  [username password]
  {:primary-key (u/generate-squuid)
   :username    username
   :passhash    (ua/hash-password password)})

(s/fdef validate-admin-input
  :args (s/cat :username ::as/username :password ::as/password)
  :ret as/query-validate-admin-input-spec)

(defn validate-admin-input
  [username password]
  {:username username
   :password password})

(s/fdef delete-admin-input
  :args (s/cat :account-id ::as/account-id)
  :ret as/delete-admin-input-spec)

(defn delete-admin-input
  [account-id]
  {:account-id account-id})
