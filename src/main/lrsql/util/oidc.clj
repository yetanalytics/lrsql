(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs.auth :as lrs-auth]
            [lrsql.init.authority :as authority]
            [lrsql.spec.admin :as admin]
            [lrsql.spec.config :as config]
            [lrsql.util.auth :as auth]))

;; OIDC supports an additional scope `admin` that allows all admin actions

(def oidc-scope-str-kw-map
  {"admin" :scope/admin})

(s/def ::scope
  #{:scope/admin})

(defn- get-scope-map*
  [scope-map
   prefix]
  (reduce-kv
   (fn [m k v]
     (assoc
      m
      (str prefix k)
      v))
   {}
   scope-map))

(def get-scope-map
  (memoize get-scope-map*))

(s/def ::scope-prefix ::config/oidc-scope-prefix)
(s/def ::scope-map (s/map-of string? qualified-keyword?))

(s/fdef parse-scope-claim
  :args (s/cat :scope-str string?
               :kwargs (s/keys*
                        :opt-un [::scope-prefix
                                 ::scope-map]))
  :ret (s/nonconforming
        (s/or
         :lrs-scopes
         (s/every ::auth/scope)
         :admin-scopes
         (s/every ::scope))))

(defn parse-scope-claim
  "Parse the scope claim and match to lrsql scope keywords, prepending
  any scope-prefix provided before matching"
  [scope-str
   & {:keys [scope-map
             scope-prefix]
      :or   {scope-map    auth/scope-str-kw-map
             scope-prefix ""}}]
  (keep (get-scope-map scope-map scope-prefix)
        (cs/split scope-str #"\s")))

(s/def :lrsql.util.oidc.token-auth-identity/result
  (s/nonconforming
   (s/or :unauthorized #{::lrs-auth/unauthorized}
         :auth-identity (s/keys :req-un [::auth/scopes]))))

(s/fdef token-auth-identity
  :args (s/cat :ctx map?
               :authority-fn fn?
               :scope-prefix ::config/oidc-scope-prefix)
  :ret (s/nilable
        (s/keys :req-un [:lrsql.util.oidc.token-auth-identity/result])))

(defn token-auth-identity
  "For the given context, return a valid auth identity from token claims. If no
  claims are present, return nil.
  args:
    ctx - Pedestal context.
    authority-fn - A function that accepts claims and returns an authority.
    scope-prefix - Prefix to add to expected scopes.
  "
  [ctx
   authority-fn
   scope-prefix]
  (when-let [token (:com.yetanalytics.pedestal-oidc/token ctx)]
    (let [{:keys [scope]
           :as   claims} (get-in ctx
                               [:request
                                :com.yetanalytics.pedestal-oidc/claims])]
      {:result
       (if-let [scopes (some-> scope
                               (parse-scope-claim
                                :scope-prefix scope-prefix)
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
                   (log/warn "Could not render authority for OIDC claims")
                   :com.yetanalytics.lrs.auth/unauthorized)
                 ::authority/invalid-json
                 (do
                   (log/warn "Could not render authority for OIDC, json invalid")
                   :com.yetanalytics.lrs.auth/unauthorized)
                 (throw ex)))))
         ;; no valid scopes, can't do anything
         :com.yetanalytics.lrs.auth/unauthorized)})))

;; Only auth scopes
(s/def ::scopes
  (s/every ::scope :kind set? :into #{}))

(s/def ::oidc-admin-identity
  (s/keys :req-un [::scopes
                   ::admin/username
                   :lrsql.spec.admin.input/oidc-issuer]))

(s/def :lrsql.util.oidc.token-auth-admin-identity/result
  (s/nonconforming
   (s/or :unauthorized #{::unauthorized}
         :auth-identity
         ::oidc-admin-identity)))

(s/fdef token-auth-admin-identity
  :args (s/cat :ctx map?
               :scope-prefix ::config/oidc-scope-prefix)
  :ret (s/nilable
        :lrsql.util.oidc.token-auth-admin-identity/result))

(defn token-auth-admin-identity
  "For the given context, return a valid OIDC admin auth identity from token
  claims.
  args:
    ctx - Pedestal context that may contain claims.
    scope-prefix - Prefix to add to expected scopes."
  [ctx
   scope-prefix]
  (when (:com.yetanalytics.pedestal-oidc/token ctx)
    (let [{:keys [scope iss sub]} (get-in ctx
                                          [:request
                                           :com.yetanalytics.pedestal-oidc/claims])]
      (if-let [scopes (and (not-empty iss)
                           (not-empty sub)
                           (some-> scope
                                   (parse-scope-claim
                                    :scope-prefix scope-prefix
                                    :scope-map oidc-scope-str-kw-map)
                                   not-empty
                                   (->> (into #{}))))]
        {:scopes      scopes
         :username    sub
         :oidc-issuer iss}
        ;; no valid scopes, can't do anything
        ::unauthorized))))

(s/fdef authorize-admin-action
  :args (s/cat :ctx           (s/keys :req-un [::auth/request])
               :auth-identity (s/keys :req-un [::scopes]))
  :ret (s/keys :req-un [::auth/result]))

(defn authorize-admin-action
  "Given a pedestal context and an OIDC admin auth identity, authorize or deny."
  [{{:keys [path-info]} :request
    :as _ctx}
   {:keys [scopes]
    :as _auth-identity}]
  {:result
   (boolean
    (and (cs/starts-with? path-info "/admin")
         (contains? scopes :scope/admin)))})
