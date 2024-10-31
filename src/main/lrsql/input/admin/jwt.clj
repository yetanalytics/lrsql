(ns lrsql.input.admin.jwt
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin.jwt :as jwts]
            [lrsql.util :as u]))

(s/fdef query-blocked-jwt-input
  :args (s/cat :account-id ::jwts/account-id)
  :ret jwts/query-blocked-jwt-input-spec)

(defn query-blocked-jwt-input
  [account-id]
  {:account-id   account-id
   :current-time (u/current-time)})

(s/fdef insert-blocked-jwt-input
  :args (s/cat :account-id ::jwts/account-id
               :expiration ::jwts/expiration)
  :ret (s/and jwts/insert-blocked-jwt-input-spec
              jwts/delete-blocked-jwt-time-input-spec))

(defn insert-blocked-jwt-input
  [account-id expiration]
  {:account-id   account-id
   :expiration   expiration
   :current-time (u/current-time)})

(s/fdef delete-blocked-jwts-input
  :args (s/cat :account-id ::jwts/account-id)
  :ret (s/and jwts/delete-blocked-jwt-account-input-spec
              jwts/delete-blocked-jwt-time-input-spec))

(defn delete-blocked-jwts-input
  [account-id]
  {:account-id   account-id
   :current-time (u/current-time)})
