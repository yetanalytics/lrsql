(ns lrsql.input.admin.jwt
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.admin.jwt :as jwts]
            [lrsql.util :as u]))

(defn- eviction-time
  "Generate the current time, offset by `exp` number of seconds later."
  [exp]
  (-> (u/current-time)
      (u/offset-time exp :seconds)))

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
  :args (s/cat)
  :ret jwts/purge-blocklist-input-spec)

(defn purge-blocklist-input
  []
  {:current-time (u/current-time)})
