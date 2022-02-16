(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [lrsql.spec.config :as config]))

(s/fdef get-configuration
  :args (s/cat :webserver ::config/webserver)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :openid-issuer or :openid-config."
  [{:keys [openid-issuer
           openid-config]}]
  (when-let [config-uri (or openid-config
                            (and openid-issuer
                                 (disco/issuer->config-uri openid-issuer)))]
    (disco/get-openid-config config-uri)))
