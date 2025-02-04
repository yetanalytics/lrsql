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
               :label      ::as/label
               :key-pair   as/key-pair-args-spec)
  :ret as/insert-cred-input-spec)

(defn insert-credential-input
  "Given `account-id` and either a `key-pair` map or separate `api-key` and
   `secret-key` args, construct the input param map for `insert-credential!`"
  ([account-id label key-pair]
   (assoc key-pair
          :primary-key (u/generate-squuid)
          :account-id  account-id
          :label       label))
  ([account-id label api-key secret-key]
   {:primary-key (u/generate-squuid)
    :api-key     api-key
    :secret-key  secret-key
    :account-id  account-id
    :label       label}))

(s/fdef insert-credential-scopes-input
  :args (s/cat :key-pair as/key-pair-args-spec
               :scopes   ::as/scopes)
  :ret as/insert-cred-scopes-input-spec)

(defn insert-credential-scopes-input
  "Given a coll of `scopes` and either a `key-pair` map or separate
   `api-key` and `secret-key` args, construct the input param map for
   `insert-credential-scopes!`"
  ([key-pair scopes]
   (->> scopes
        (map (partial assoc key-pair :scope))
        (map (fn [skp] (assoc skp :primary-key (u/generate-squuid))))))
  ([api-key secret-key scopes]
   (let [key-pair {:api-key    api-key
                   :secret-key secret-key}]
     (insert-credential-scopes-input key-pair scopes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Update
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef update-credential-label-input
  :args (s/cat :label    ::as/label
               :key-pair as/key-pair-args-spec))

(defn update-credential-label-input
  "Given a `label` and either a `key-pair` map or seperate `api-key` and
   `secret-key` args, construct the input param map for
   `update-credential-label!`"
  ([label key-pair]
   (assoc key-pair :label label))
  ([label api-key secret-key]
   {:api-key    api-key
    :secret-key secret-key
    :label      label}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credentials Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-credentials-input
  :args (s/cat :account-id ::ads/account-id
               :key-pair   as/key-pair-args-spec)
  :ret as/delete-cred-input-spec)

(defn delete-credentials-input
  "Given `account-id` and either a `key-pair` map or separate `api-key` and
   `secret-key` args, construct the input param map for `delete-credential!`"
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
  "Given a coll of `scopes` and either a `key-pair` map or separate
   `api-key` and `secret-key` args, construct the input param map for
   `delete-credential-scopes!`"
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
  "Given `account-id`, construct the input param map for `query-credential`
   to query the keys associated with the account."
  [account-id]
  {:account-id account-id})

(s/fdef query-credential-scopes*-input
  :args as/key-pair-args-spec
  :ret as/query-cred-scopes*-input-spec)

(defn query-credential-scopes*-input
  "Given either a `key-pair` map or separate `api-key` and `secret-key` args,
   construct the input param map for `query-credential-scopes*`."
  ([key-pair]
   key-pair)
  ([api-key secret-key]
   {:api-key    api-key
    :secret-key secret-key}))

(s/fdef query-credential-scopes-input
  :args as/key-pair-authority-args-spec
  :ret as/query-cred-scopes-input-spec)

(defn query-credential-scopes-input
  "Given either a `key-pair` map or separate `api-key` and `secret-key` args,
   alongside `authority-fn` and `authority-url` args for agent creation,
   construct the input param map for `query-credential-scopes`."
  ([authority-fn authority-url key-pair]
   (merge
    key-pair
    {:authority-fn  authority-fn
     :authority-url authority-url}))
  ([authority-fn authority-url api-key secret-key]
   {:api-key       api-key
    :secret-key    secret-key
    :authority-fn  authority-fn
    :authority-url authority-url}))
