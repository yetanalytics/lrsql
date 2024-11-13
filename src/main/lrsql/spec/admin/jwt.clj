(ns lrsql.spec.admin.jwt
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]
            [lrsql.spec.config :as config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-jwt-backend?
  [bk]
  (satisfies? bp/JWTBlocklistBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::jwt string?)
(s/def ::exp ::config/jwt-exp-time)
(s/def ::leeway ::config/jwt-exp-leeway)
(s/def ::eviction-time c/instant-spec)
(s/def ::current-time c/instant-spec)

(def query-blocked-jwt-input-spec
  (s/keys :req-un [::jwt]))

(def insert-blocked-jwt-input-spec
  (s/keys :req-un [::jwt ::eviction-time]))

(def purge-blocklist-input-spec
  (s/keys :req-un [::current-time]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :lrsql.spec.admin.jwt.command/result ::jwt)

(def blocked-jwt-op-result-spec
  (s/keys :req-un [:lrsql.spec.admin.jwt.command/result]))

(def blocked-jwt-query-result-spec
  boolean?)
