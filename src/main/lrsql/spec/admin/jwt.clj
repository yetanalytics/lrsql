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

(s/def ::account-id :lrsql.spec.admin/account-id)
(s/def ::current-time c/instant-spec)
(s/def ::expiration c/instant-spec)
(s/def ::leeway ::config/jwt-exp-leeway)

(def query-blocked-jwt-input-spec
  (s/keys :req-un [::account-id
                   ::current-time]))

(def insert-blocked-jwt-input-spec
  (s/keys :req-un [::account-id
                   ::expiration]))

(def delete-blocked-jwt-account-input-spec
  (s/keys :req-un [::account-id]))

(def delete-blocked-jwt-time-input-spec
  (s/keys :req-un [::current-time]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :lrsql.spec.admin.jwt.command/result ::account-id)

(def blocked-jwt-op-result-spec
  (s/keys :req-un [:lrsql.spec.admin.jwt.command/result]))

(def blocked-jwt-query-result-spec
  boolean?)
