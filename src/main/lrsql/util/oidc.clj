(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs.auth :as lrs-auth]
            [lrsql.init.authority :as authority]
            [lrsql.spec.config :as config]
            [lrsql.util.auth :as auth])
  (:import [java.io File]))

(defn- get-scope-map*
  [prefix]
  (reduce-kv
   (fn [m k v]
     (assoc
      m
      (str prefix k)
      v))
   {}
   auth/scope-str-kw-map))

(def get-scope-map
  (memoize get-scope-map*))

(s/def ::scope-prefix ::config/oidc-scope-prefix)

(s/fdef parse-scope-claim
  :args (s/cat :scope-str string?
               :kwargs (s/keys*
                        :opt-un [::scope-prefix]))
  :ret (s/every ::auth/scope))

(defn parse-scope-claim
  "Parse the scope claim and match to lrsql scope keywords, prepending
  any scope-prefix provided before matching"
  [scope-str
   & {:keys [scope-prefix]
      :or   {scope-prefix ""}}]
  (keep (get-scope-map scope-prefix)
        (cs/split scope-str #"\s")))

(s/fdef token-auth-identity
  :args (s/cat :ctx map?
               :authority-fn fn?
               :scope-prefix ::config/oidc-scope-prefix)
  :ret (s/nilable
        ::lrs-auth/identity))

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
