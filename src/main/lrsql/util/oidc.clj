(ns lrsql.util.oidc
  "OpenID Connect Utilities"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs.auth :as lrs-auth]
            [lrsql.init.authority :as authority]
            [lrsql.util.auth :as auth])
  (:import [java.io File]))

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
                   (log/warn "Could not render authority for OIDC claims")
                   :com.yetanalytics.lrs.auth/unauthorized)
                 ::authority/invalid-json
                 (do
                   (log/warn "Could not render authority for OIDC, json invalid")
                   :com.yetanalytics.lrs.auth/unauthorized)
                 (throw ex)))))
         ;; no valid scopes, can't do anything
         :com.yetanalytics.lrs.auth/unauthorized)})))
