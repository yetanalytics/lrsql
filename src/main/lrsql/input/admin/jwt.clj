(ns lrsql.input.admin.jwt
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin.jwt :as jwts]
            [lrsql.util :as u]))

(defn- eviction-time
  "Takes a jwt expiry interval ('seconds from now'), and returns an *eviction instant*, the time at which the jwt has expired and no longer needs to be blocked."
  [exp]
  (-> (u/current-time)
      (u/offset-time (inc exp) :seconds)))

(defn- current-time
  "Generate the current time, offset by `leeway` number of seconds earlier.
   
   See: `buddy.sign.jwt/validate-claims`"
  [leeway]
  (-> (u/current-time)
      (u/offset-time (* -1 leeway) :seconds)))

(s/fdef query-blocked-jwt-input
  :args (s/cat :jwt ::jwts/jwt)
  :ret jwts/query-blocked-jwt-input-spec)

(defn query-blocked-jwt-input
  [jwt]
  {:jwt jwt})

(s/fdef insert-blocked-jwt-input
  :args (s/cat :jwt ::jwts/jwt
               :exp ::jwts/exp)
  :ret jwts/insert-blocked-jwt-input-spec)

(defn insert-blocked-jwt-input
  [jwt exp]
  {:jwt           jwt
   :eviction-time (eviction-time exp)})

(s/fdef purge-blocklist-input
  :args (s/cat :leeway ::jwts/leeway)
  :ret jwts/purge-blocklist-input-spec)

(defn purge-blocklist-input
  [leeway]
  {:current-time (current-time leeway)})

;; One-time JWTs

(s/fdef insert-one-time-jwt-input
  :args (s/cat :jwt ::jwts/jwt
               :exp ::jwts/exp
               :oti ::jwts/one-time-id)
  :ret jwts/insert-one-time-jwt-input-spec)

(defn insert-one-time-jwt-input
  [jwt exp oti]
  {:jwt           jwt
   :eviction-time (eviction-time exp)
   :one-time-id   oti})

(s/fdef update-one-time-jwt-input
  :args (s/cat :jwt ::jwts/jwt
               :oti ::jwts/one-time-id))

(defn update-one-time-jwt-input
  [jwt oti]
  {:jwt         jwt
   :one-time-id oti})
