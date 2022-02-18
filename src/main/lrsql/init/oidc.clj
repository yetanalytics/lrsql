(ns lrsql.init.oidc
  "OIDC initialization"
  (:require [clojure.core.memoize :as mem]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as jwt]
            [io.pedestal.interceptor :as i]
            [lrsql.init.authority :as authority]
            [lrsql.spec.config :as config]
            [lrsql.spec.oidc :as oidc]
            [selmer.parser :as selm-parser]
            xapi-schema.spec)
  (:import [java.io File]))

(defn- select-config
  [config]
  (select-keys
   config
   [:oidc-issuer
    :oidc-config
    :jwks-uri]))

(def partial-config-spec
  (s/keys :opt-un [::config/oidc-issuer
                   ::config/oidc-config
                   ::config/jwks-uri]))

(s/fdef get-configuration
  :args (s/cat :config partial-config-spec)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :oidc-issuer or :oidc-config."
  [{:keys [oidc-issuer
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
  :args (s/cat :config partial-config-spec)
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

;; Authority

(s/fdef resolve-authority-claims
  :args (s/cat :claims ::oidc/claims)
  :ret ::oidc/authority-claims)

(defn resolve-authority-claims
  "Given claims from an Access Token derive and add:
  * :lrsql/resolved-client-id - a reliable client id to use in the authority
    template."
  [{:keys [aud
           azp
           client_id]
    :as claims}]
  (assoc claims
         :lrsql/resolved-client-id
         (or client_id
             azp
             (if (string? aud)
               aud
               (first aud)))))

(s/fdef make-authority-fn
  :args (s/cat :template-path (s/nilable string?)
               :threshold (s/? pos-int?))
  :ret (s/fspec
        :args (s/cat :context-map ::oidc/claims)
        :ret :statement/authority))

(def default-authority-fn
  "The default precompiled function to render authority"
  (-> "lrsql/config/oidc_authority.json.template"
      io/resource
      selm-parser/parse*
      authority/make-authority-fn*))

(defn make-authority-fn
  "Like authority/make-authority-fn but produces a function expecting OIDC
  claims."
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
    (mem/lru (comp authority-fn
                   resolve-authority-claims)
             :lru/threshold (or threshold 512))))
