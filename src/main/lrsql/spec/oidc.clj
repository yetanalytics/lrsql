(ns lrsql.spec.oidc
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]))

;; Claims https://www.iana.org/assignments/jwt/jwt.xhtml

(s/def ::scope
  (s/with-gen string?
    (fn []
      (sgen/return
       "openid all"))))

(s/def ::iss string?)
(s/def ::sub string?)
(s/def ::aud
  (s/or :scalar string?
        :array (s/every string? :min-count 1)))

(s/def ::azp string?)
(s/def ::client_id string?)

(s/def ::claims
  (s/with-gen
    (s/keys :req-un [::scope
                     ::iss
                     ::sub
                     ::aud]
            :opt-un [::azp
                     ::client_id])
    (fn []
      (sgen/return {:scope "openid all"
                    :iss   "http://example.com/realm"
                    :aud   "someapp"
                    :sub   "1234"}))))

;; This is a special namespaced keyword with a single segment (so we don't need
;; to escape the dots) for inclusion in the OIDC authority template
;; https://github.com/yogthos/Selmer#namespaced-keys
(s/def :lrsql/resolved-client-id string?)

(s/def ::authority-claims
  (s/merge
   (s/keys :req [:lrsql/resolved-client-id])
   ::claims))
