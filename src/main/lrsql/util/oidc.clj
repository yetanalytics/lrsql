(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.core.memoize :as mem]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs.auth :as lrs-auth]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as jwt]
            [io.pedestal.interceptor :as i]
            [lrsql.init.authority :as authority]
            [lrsql.spec.config :as config]
            [lrsql.util.auth :as auth]
            [selmer.parser :as selm-parser]
            [xapi-schema.spec :as xs])
  (:import [java.io File]))

(defn- select-config
  [config]
  (select-keys
   config
   [:oidc-issuer
    :oidc-config
    :jwks-uri]))

(s/fdef get-configuration
  :args (s/cat :config ::config/webserver)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :oidc-issuer or :oidc-config."
  [{:keys               [oidc-issuer
           oidc-config] :as config}]
  (try
    (when-let [config-uri (or oidc-config
                              (and oidc-issuer
                                   (disco/issuer->config-uri oidc-issuer)))]
      (disco/get-openid-config config-uri))
    (catch AssertionError ae
      (ex-info "Invalid OIDC Config"
               {:type        ::invalid-config
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
        {:type        ::init-failure
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
  :args (s/cat :ctx map?
               :authority-fn fn?)
  :ret (s/nilable
        ::lrs-auth/identity))

(defn token-auth-identity
  "For the given context, return a valid auth identity from token claims. If no
  claims are present, return nil"
  [ctx
   authority-fn]
  (when-let [token (:com.yetanalytics.pedestal-oidc/token ctx)]
    (let [{:keys [scope]
           :as   claims} (get-in ctx
                               [:request
                                :com.yetanalytics.pedestal-oidc/claims])]
      {:result
       (if-let [scopes (some-> scope
                               parse-scope-claim
                               not-empty
                               (->> (into #{})))]
         (try
           {:scopes scopes
            :prefix ""
            :auth   {:token token}
            :agent  (authority-fn claims)}
           (catch clojure.lang.ExceptionInfo ex
             (let [{ex-type :type} (ex-data ex)]
               (case ex-type
                 ::authority/unknown-variable
                 (do
                   (log/warnf "Could not render authority for OIDC claims %s"
                              (pr-str claims))
                   :com.yetanalytics.lrs.auth/unauthorized)
                 ::authority/invalid-json
                 (do
                   (log/warn "Could not render authority for OIDC, json invalid")
                   :com.yetanalytics.lrs.auth/unauthorized)
                 (throw ex)))))
         ;; no valid scopes, can't do anything
         :com.yetanalytics.lrs.auth/unauthorized)})))

;; Authority

(s/fdef make-authority-fn
  :args (s/cat :template-path (s/nilable string?)
               :threshold (s/? pos-int?))
  :ret (s/fspec
        :args (s/cat :context-map
                     (s/with-gen map?
                       (fn []
                         (sgen/return
                          {:scope "openid all"
                           :iss   "http://example.com/realm"
                           :aud   "someapp"
                           :sub   "1234"}))))
        :ret ::xs/agent))

(def default-authority-fn
  "The default precompiled function to render authority"
  (-> "lrsql/config/oidc_authority.json.template"
      io/resource
      selm-parser/parse*
      authority/make-authority-fn*))

(defn make-authority-fn
  "Like authority/make-authority-fn but does not have a specified context map.
  Does not perform assertions."
  [template-path & [threshold]]
  (let [^File f
        (io/file template-path)
        authority-fn
        (if (and f (.exists f))
          ;; Override template supplied - use that
          (let [template (selm-parser/parse* f)]
            (authority/make-authority-fn* template))
          ;; Override template not supplied - fall back to default
          default-authority-fn)]
    (mem/lru authority-fn
             :lru/threshold (or threshold 512))))
