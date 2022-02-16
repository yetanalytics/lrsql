(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as jwt]
            [lrsql.spec.config :as config]
            [io.pedestal.interceptor :as i]))

(defn- select-config
  [config]
  (select-keys
   config
   [:openid-issuer
    :openid-config
    :jwks-uri]))

(s/fdef get-configuration
  :args (s/cat :config ::config/webserver)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :openid-issuer or :openid-config."
  [{:keys [openid-issuer
           openid-config] :as config}]
  (try
    (when-let [config-uri (or openid-config
                              (and openid-issuer
                                   (disco/issuer->config-uri openid-issuer)))]
      (disco/get-openid-config config-uri))
    (catch AssertionError ae
      (ex-info "Invalid OIDC Config"
               {:type ::invalid-config
                :oidc-config (select-config config)}
               ae))))

(s/fdef resource-interceptors
  :args (s/cat :config ::config/webserver)
  :ret (s/every i/interceptor?))

(defn resource-interceptors
  "Given a webserver config, return a (possibly empty) vector of interceptors.
  Interceptors will enable token auth against OIDC"
  [{:keys [jwks-uri] :as config}]
  (try
    (if-let [jwks-uri (or jwks-uri
                          (some-> config
                                  get-configuration
                                  (get "jwks_uri")))]
      (let [keyset-cache (atom (jwt/get-keyset jwks-uri))]
        [;; Decode/Unsign tokens
         (oidc-i/decode-interceptor
          (fn [_]
            (fn [kid]
              (get @keyset-cache kid
                   ;; If kid is not found in the keyset, attempt refresh and try
                   ;; again
                   (get (reset! keyset-cache (jwt/get-keyset jwks-uri))
                        kid))))
          :required? false)
         ;; TODO: parse out scopes + authorize
         ])
      ;; If no config, don't return any
      [])
    (catch Exception ex
      (throw
       (ex-info
        "OIDC Initialization Failure"
        {:type ::init-failure
         :oidc-config (select-config config)}
        ex)))))
