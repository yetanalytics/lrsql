(ns lrsql.input.admin.jwt
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin.jwt :as jwts]
            [lrsql.util :as u]))

(defn- current-time
  "Generate the current time, offset by `leeway` number of seconds earlier.
   
   See: `buddy.sign.jwt/validate-claims`"
  [leeway]
  (-> (u/current-time)
      (u/offset-time (* -1 leeway) :seconds)))

(s/fdef query-blocked-jwt-input
  :args (s/cat :account-id ::jwts/account-id
               :leeway ::jwts/leeway)
  :ret jwts/query-blocked-jwt-input-spec)

(defn query-blocked-jwt-input
  [account-id leeway]
  {:account-id   account-id
   :current-time (current-time leeway)})

(s/fdef insert-blocked-jwt-input
  :args (s/cat :account-id ::jwts/account-id
               :expiration ::jwts/expiration
               :leeway ::jwts/leeway)
  :ret (s/and jwts/insert-blocked-jwt-input-spec
              jwts/delete-blocked-jwt-time-input-spec))

(defn insert-blocked-jwt-input
  [account-id expiration leeway]
  {:account-id   account-id
   :expiration   expiration
   :current-time (current-time leeway)})

(s/fdef delete-blocked-jwts-input
  :args (s/cat :account-id ::jwts/account-id
               :leeway ::jwts/leeway)
  :ret (s/and jwts/delete-blocked-jwt-account-input-spec
              jwts/delete-blocked-jwt-time-input-spec))

(defn delete-blocked-jwts-input
  [account-id leeway]
  {:account-id   account-id
   :current-time (current-time leeway)})
