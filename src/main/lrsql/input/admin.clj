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

(s/fdef delete-admin-input
  :args (s/cat :account-id ::ads/account-id)
  :ret ads/delete-admin-input-spec)

(defn delete-admin-input
  "Given `username` and `password`, construct the input param map for
   `delete-admin!`."
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
