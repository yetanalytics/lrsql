(ns lrsql.init.oidc
  "OIDC initialization"
  (:require [clojure.core.memoize :as mem]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [com.yetanalytics.pedestal-oidc.discovery :as disco]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as jwt]
            [io.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.oidc :as admin-oidc]
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
    :oidc-audience
    :oidc-verify-remote-issuer
    :oidc-config
    :jwks-uri]))

(def partial-config-spec
  (s/keys :opt-un [::config/oidc-issuer
                   ::config/oidc-audience
                   ::config/oidc-verify-remote-issuer
                   ::config/oidc-config
                   ::config/jwks-uri]))

(s/fdef get-configuration
  :args (s/cat :config partial-config-spec)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :oidc-issuer or :oidc-config."
  [{:keys [oidc-issuer
           oidc-verify-remote-issuer
           oidc-config]
    :or {oidc-verify-remote-issuer true}
    :as config}]
  (try
    (when-let [config-uri (or oidc-config
                              (and oidc-issuer
                                   (disco/issuer->config-uri oidc-issuer)))]
      (let [{:strs [issuer]
             :as   remote-config} (disco/get-openid-config config-uri)]
        ;; Verify that issuer matches if passed in
        (when (and oidc-issuer oidc-verify-remote-issuer)
          (when-not (= oidc-issuer issuer)
            (throw
             (ex-info
              (format
               "Specified oidc-issuer %s does not match remote %s."
               oidc-issuer
               issuer)
              {:type          ::issuer-mismatch
               :local-issuer  oidc-issuer
               :remote-issuer issuer}))))
        ;; Return it
        remote-config))
    (catch Exception ex
      (throw
       (ex-info "Invalid OIDC Config"
                {:type        ::invalid-config
                 :oidc-config (select-config config)}
                ex)))))

(s/fdef resource-interceptors
  :args (s/cat :config partial-config-spec)
  :ret (s/every i/interceptor?))

(defn resource-interceptors
  "Given a webserver config, return a (possibly empty) vector of interceptors.
  Interceptors will enable token auth against OIDC."
  [{:keys [jwks-uri
           oidc-issuer
           oidc-audience] :as config}]
  (try
    (if-let [jwks-uri (or jwks-uri
                          (some-> config
                                  get-configuration
                                  (get "jwks_uri")))]
      (let [_ (when-not oidc-issuer
                (log/warn "oidc-issuer should be provided for verification"))
            _ (when-not oidc-audience
                (log/warn "oidc-audience should be provided for verification"))
            keyset-cache (atom (jwt/get-keyset jwks-uri))]
        [;; Decode/Unsign tokens
         (oidc-i/decode-interceptor
          (fn [_]
            (fn [kid]
              (get @keyset-cache kid
                   ;; If kid is not found in the keyset, attempt refresh and try
                   ;; again
                   (get (reset! keyset-cache (jwt/get-keyset jwks-uri))
                        kid))))
          :required? false
          :unauthorized
          ;; Allow unknown/nil key IDs through for possible subsequent handling
          (fn [ctx failure & rest-args]
            (if (= :kid-not-found failure)
              ctx
              (apply oidc-i/default-unauthorized ctx failure rest-args)))
          :unsign-opts
          (cond-> {}
            ;; Apply issuer verification
            oidc-issuer (assoc :iss oidc-issuer)
            ;; Apply audience verification
            oidc-audience (assoc :aud oidc-audience)))
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

(s/fdef admin-interceptors
  :args (s/cat :config partial-config-spec)
  :ret (s/every i/interceptor?))

(defn admin-interceptors
  "Given a webserver config, return a (possibly empty) vector of interceptors
  for use with the admin API. These validate token claims and ensure an admin
  account is made."
  [{:keys [jwks-uri
           oidc-issuer
           oidc-config] :as config}]
  (if (or jwks-uri
          oidc-issuer
          oidc-config)
    [admin-oidc/validate-oidc-identity
     admin-oidc/authorize-oidc-request
     admin-oidc/ensure-oidc-identity]
    []))

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
