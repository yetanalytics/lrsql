(ns lrsql.init.oidc
  "OIDC initialization"
  (:require [cheshire.core :as json]
            [clojure.core.memoize :as mem]
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
            [selmer.util :as selm-u]
            xapi-schema.spec)
  (:import [java.io File]))

(defn- select-config
  [config]
  (select-keys
   config
   [:oidc-issuer
    :oidc-audience
    :oidc-verify-remote-issuer
    :oidc-enable-local-admin]))

(def partial-config-spec
  (s/keys :opt-un [::config/oidc-issuer
                   ::config/oidc-audience
                   ::config/oidc-verify-remote-issuer
                   ::config/oidc-enable-local-admin]))

(s/fdef get-configuration
  :args (s/cat :config partial-config-spec)
  :ret (s/nilable map?))

(defn get-configuration
  "Given webserver config, return an openid configuration if one is specified
  via :oidc-issuer."
  [{:keys [oidc-issuer
           oidc-verify-remote-issuer]
    :or {oidc-verify-remote-issuer true}
    :as config}]
  (try
    (when-let [config-uri (and oidc-issuer
                               (disco/issuer->config-uri oidc-issuer))]
      (let [{:strs [issuer]
             :as   remote-config} (disco/get-openid-config config-uri)]
        ;; Verify that issuer matches if passed in
        (when oidc-verify-remote-issuer
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
  :args (s/cat :config (s/merge partial-config-spec
                                (s/keys :req-un [::config/jwt-exp-leeway])))
  :ret (s/every i/interceptor?))

(defn resource-interceptors
  "Given a webserver config, return a (possibly empty) vector of interceptors.
  Interceptors will enable token auth against OIDC."
  [{:keys [oidc-issuer
           oidc-audience
           jwt-exp-leeway] :as config}]
  (try
    (if-let [jwks-uri (some-> config
                              get-configuration
                              (get "jwks_uri"))]
      (let [_ (when-not oidc-audience
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
          (cond-> {:iss    oidc-issuer
                   :leeway jwt-exp-leeway}
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
  [{:keys [oidc-issuer
           oidc-enable-local-admin]}]
  (if oidc-issuer
    (cond-> [admin-oidc/validate-oidc-identity
             admin-oidc/authorize-oidc-request
             admin-oidc/ensure-oidc-identity]
      ;; If local admin is disabled (default), prevent subsequent login
      (not oidc-enable-local-admin)
      (conj admin-oidc/require-oidc-identity))
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

(def default-client-template
  (-> "lrsql/config/oidc_client.json.template"
      io/resource
      selm-parser/parse*))

(defn- get-client-template
  [template-loc]
  (if (not-empty template-loc)
    (let [f (io/file template-loc)]
      (if (.exists f)
        (selm-parser/parse* f)
        default-client-template))
    default-client-template))

(defn- throw-on-missing
  "When a user enters a variable and it is not in our context map, throw!
   Used by selmer when context map validation fails."
  [tag context-map]
  (throw
   (ex-info (format "\"%s\" is not a valid variable for OIDC client config template."
                    (:tag-value tag))
            {:type        ::unknown-variable
             :tag         tag
             :context-map context-map})))

(s/def :lrsql.init.oidc.render-client-config/lrs
  (s/keys :req-un [::config/oidc-scope-prefix]))

(s/def :lrsql.init.oidc.render-client-config/webserver
  (s/keys :req-un [::config/oidc-issuer
                   ::config/oidc-audience
                   ::config/oidc-client-id
                   ::config/oidc-client-template]))

(s/fdef render-client-config
  :args (s/cat :config
               (s/keys
                :req-un
                [:lrsql.init.oidc.render-client-config/lrs
                 :lrsql.init.oidc.render-client-config/webserver]))
  :ret map?)

(defn render-client-config
  "Render OIDC client config from template."
  [{{:keys [oidc-client-template]} :webserver
    :as                            config}]
  (binding [selm-u/*missing-value-formatter* throw-on-missing
            selm-u/*filter-missing-values*   (constantly false)]
    (json/parse-string
     (selm-parser/render-template
      (get-client-template oidc-client-template)
      config))))

(s/fdef admin-ui-interceptors
  :args (s/cat :webserver-config ::config/webserver
               :lrs-config       ::config/lrs)
  :ret (s/every i/interceptor?))

(defn admin-ui-interceptors
  "Given webserver and LRS configs, return a vector of interceptors to apply to
  Admin UI routes. If webserver oidc-client-id is not specified, returns an
  empty vector."
  [{:keys [oidc-issuer
           oidc-client-id
           oidc-enable-local-admin]
    :as   webserver-config}
   lrs-config]
  (if (and oidc-issuer
           oidc-client-id)
    [(admin-oidc/inject-admin-env
      {:oidc                    (render-client-config
                                 {:webserver webserver-config
                                  :lrs       lrs-config})
       :oidc-enable-local-admin oidc-enable-local-admin})]
    []))

(s/def ::resource-interceptors
  (s/every i/interceptor?))
(s/def ::admin-interceptors
  (s/every i/interceptor?))
(s/def ::admin-ui-interceptors
  (s/every i/interceptor?))

(s/fdef interceptors
  :args (s/cat :webserver-config ::config/webserver
               :lrs-config       ::config/lrs)
  :ret (s/keys :req-un [::resource-interceptors
                        ::admin-interceptors
                        ::admin-ui-interceptors]))

(defn interceptors
  "Given webserver and LRS configs, return a map with three (possibly empty)
  vectors of interceptors:
    :resource-interceptors - API-side OIDC token support.
    :admin-interceptors - Validation and authn for admin resources.
    :admin-ui-interceptors - Inject OIDC client configuration."
  [webserver-config
   lrs-config]
  (let [resource (resource-interceptors webserver-config)]
    {:resource-interceptors resource
     :admin-interceptors    (into resource
                                  (admin-interceptors
                                   webserver-config))
     :admin-ui-interceptors (admin-ui-interceptors
                             webserver-config
                             lrs-config)}))
