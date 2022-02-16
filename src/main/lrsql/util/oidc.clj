(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as cs]
            [com.yetanalytics.lrs.auth :as lrs-auth]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as jwt]
            [lrsql.util.auth :as auth]
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
         ;; This is a vector in case we need additional interceptors. At present
         ;; we do not.
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

(s/fdef parse-scope-claim
  :args (s/cat :scope-str string?)
  :ret (s/every ::auth/scope))

(defn parse-scope-claim
  [scope-str]
  (keep auth/scope-str->kw
       (cs/split scope-str #"\s")))

(s/fdef token-auth-identity
  :args (s/cat :ctx map?)
  :ret (s/nilable
        ::lrs-auth/identity))

(defn token-auth-identity
  "For the given context, return a valid auth identity from token claims. If no
  claims are present, return nil"
  [ctx]
  (when-let [token (:com.yetanalytics.pedestal-oidc/token ctx)]
    (let [{:keys [scope
                  iss
                  azp
                  sub]} (get-in ctx
                                [:request
                                 :com.yetanalytics.pedestal-oidc/claims])]
      {:result
       (if-let [scopes (some-> scope
                               parse-scope-claim
                               not-empty
                               (->> (into #{})))]
         (let [;; Roughly following https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#oauth-credentials-as-authority
               authority {"objectType" "Group"
                          "member"     [{"account"
                                         {"homePage" iss
                                          "name"     azp}}
                                        {"account"
                                         {"homePage" iss
                                          "name"     sub}}]}]
           {:scopes scopes
            :prefix ""
            :auth   {:token token}
            :agent  authority})
         ;; no valid scopes, can't do anything
         :com.yetanalytics.lrs.auth/unauthorized)})))
