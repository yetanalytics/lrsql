(ns lrsql.admin.interceptors.oidc
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.interceptors.jwt :as jwt]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.oidc :as oidc]))

(def validate-oidc-identity
  "If the context has OIDC token claims, parses out data for OIDC admin identity.
  If claims are invalid, return a 401. If no clams are present, a no-op."
  (interceptor
   {:name ::validate-oidc-identity
    :enter
    (fn validate-oidc-identity [{lrs :com.yetanalytics/lrs
                                 :as ctx}]
      (if-let [ret (oidc/token-auth-admin-identity
                    ctx
                    (get-in lrs [:config :oidc-scope-prefix]))]
        (if (= ::oidc/unauthorized ret)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Invalid JWT Claims!"}})
          (assoc ctx ::admin-identity ret))
        ;; No OIDC
        ctx))}))

;; NOTE: Right now there is only one blanket :scope/admin
;; Currently only applied when OIDC is used.
(def authorize-oidc-request
  "If an admin identity is present, check if it has the proper scope(s) for the
  given action. No-op with no identity."
  (interceptor
   {:name ::authorize-oidc-request
    :enter
    (fn authorize-oidc-request [{admin-identity ::admin-identity
                                 :as            ctx}]
      (if (or (nil? admin-identity)
              (oidc/authorize-admin-action? ctx admin-identity))
        ctx
        (assoc (chain/terminate ctx)
               :response
               {:status 401
                :body   {:error "Unauthorized Admin Action!"}})))}))

(defn- disable-jwt-interceptors
  [{queue ::chain/queue :as ctx}]
  (assoc ctx
         ::chain/queue
         (into (empty queue)
               (remove
                (comp
                 #{::jwt/validate-jwt
                   ::jwt/validate-jwt-account}
                 :name)
                queue))))

(def ensure-oidc-identity
  "If an admin identity is present, create or return the user, validating the
  issuer. On success, inject the account ID and disable subsequent JWT
  interceptors. No-op with no identity."
  (interceptor
   {:name ::ensure-oidc-identity
    :enter
    (fn ensure-oidc-identity [{lrs            :com.yetanalytics/lrs
                               admin-identity ::admin-identity
                               :as            ctx}]
      (if admin-identity
        (let [{:keys [username
                      oidc-issuer]} admin-identity
              {:keys [result]} (adp/-ensure-account-oidc
                                lrs
                                username
                                oidc-issuer)]
          (if (= :lrsql.admin/oidc-issuer-mismatch-error result)
            (assoc (chain/terminate ctx)
                   :response
                   {:status 401
                    :body   {:error "OIDC Issuer Mismatch!"}})
            (-> ctx
                disable-jwt-interceptors
                (assoc-in [::jwt/data :account-id] result)
                ;; NOTE: I do this to follow what is done for native JWT
                ;; Don't think this app actually uses a session at all?
                (assoc-in
                 [:request :session ::jwt/data :account-id]
                 result))))
        ctx))}))

(defn inject-admin-env
  "Inject OIDC client configuration to merge with the admin env."
  [admin-env]
  (interceptor
   {:name ::inject-admin-env
    :enter
    (fn inject-admin-env [ctx]
      (assoc ctx ::admin-env admin-env))}))

(def require-oidc-identity
  "Verify that there is an OIDC admin identity or return a 401.
  Used to implement the oidc-enable-local-admin webserver config variable."
  (interceptor
   {:name ::require-oidc-identity
    :enter
    (fn require-oidc-identity [ctx]
      (if (::admin-identity ctx)
        ctx
        (assoc (chain/terminate ctx)
               :response
               {:status 401
                :body   {:error "Admin authentication requires OIDC!"}})))}))
