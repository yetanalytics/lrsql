(ns lrsql.admin.interceptors.jwt
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as admin-u]
            [clojure.tools.logging :as log]))

;; NOTE: These interceptors are specifically for JWT validation.
;; For JWT generation see `account/generate-jwt`.

(defn validate-jwt
  "Validate that the header JWT is valid (e.g. not expired and signed properly).
   If no-val? is true run an entirely separate decoding that gets the username
   and issuer claims, verifies a role and ensures the account if necessary."
  [secret leeway {:keys [no-val? no-val-uname no-val-issuer no-val-role-key
                         no-val-role]}]
  (interceptor
   {:name ::validate-jwt
    :enter
    (fn validate-jwt [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            token  (-> ctx
                       (get-in [:request :headers "authorization"])
                       admin-u/header->jwt)
            result (if no-val?
                     ;; decode jwt w/o validation and ensure account
                     (try
                       (let [{:keys [issuer username] :as result}
                             (admin-u/proxy-jwt->username-and-issuer
                              token no-val-uname no-val-issuer no-val-role-key
                              no-val-role)]
                         (if (some? username)
                           (:result (adp/-ensure-account-oidc lrs username issuer))
                           result))
                       (catch Exception ex
                         ;; We want any error here to return a 401, but we log
                         (log/warnf ex "No-val JWT Error: %s" (ex-message ex))
                         :lrsql.admin/unauthorized-token-error))
                     ;; normal jwt, check signature etc
                     (admin-u/jwt->account-id token secret leeway))]
        (cond
          ;; Success - assoc the account ID as an intermediate value
          (uuid? result)
          (-> ctx
              (assoc-in [::data :account-id] result)
              (assoc-in [:request :session ::data :account-id] result))
          ;; Problem with the non-validated account ensure
          (= :lrsql.admin/oidc-issuer-mismatch-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Token Issuer Mismatch!"}})
          ;; The token is bad (expired, malformed, etc.) - Unauthorized
          (= :lrsql.admin/unauthorized-token-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Unauthorized JSON Web Token!"}}))))}))

(def validate-jwt-account
  "Check that the account ID stored in the JWT exists in the account table.
   This should go after `validate-jwt`, and MUST be present if `account-id`
   is used in the route (e.g. for credential operations)."
  (interceptor
   {:name ::validate-jwt-account
    :enter
    (fn validate-jwt-account [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::data} ctx]
        (if (adp/-existing-account? lrs account-id)
          ;; Success - continue on your merry way
          ctx
          ;; The account does not exist/was deleted - Unauthorized
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Unauthorized, account does not exist!"}}))))}))
