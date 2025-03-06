(ns lrsql.admin.interceptors.jwt
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as admin-u]
            [clojure.tools.logging :as log]))

;; NOTE: These interceptors are specifically for JWT validation.
;; For JWT generation see `account/generate-jwt`.

(defn- validate-no-val-jwt
  "Proxy JWT, decode JWT without validation and ensure the account is valid."
  [lrs token {:keys [no-val-uname no-val-issuer no-val-role-key
                     no-val-role]}]
  (try
    (let [{:keys [issuer username] :as result}
          (admin-u/proxy-jwt->payload token
                                      no-val-uname
                                      no-val-issuer
                                      no-val-role-key
                                      no-val-role)]
      (if (keyword? result)
        result
        (let [{result* :result} (adp/-ensure-account-oidc lrs username issuer)]
          (if (keyword? result*)
            result*
            (assoc result :account-id result* :jwt token)))))
    (catch Exception ex
      ;; We want any error here to return a 401, but we log
      (log/warnf ex "No-val JWT Error: %s" (ex-message ex))
      :lrsql.admin/unauthorized-token-error)))

(defn- validate-jwt*
  "Normal JWT, normal signature verification and blocklist check."
  [lrs token secret leeway]
  (try
    (let [result (admin-u/jwt->payload token secret leeway)]
      (if (keyword? result)
        result
        (if-not (adp/-jwt-blocked? lrs token)
          (assoc result :jwt token)
          :lrsql.admin/unauthorized-token-error)))
    (catch Exception ex
      ;; We want any error here to return a 401, but we log
      (log/warnf ex "Unexpected JWT Error: %s" (ex-message ex))
      :lrsql.admin/unauthorized-token-error)))

(defn validate-jwt
  "Validate that the header JWT is valid (e.g. not expired and signed properly).
   If `no-val?` is true run an entirely separate decoding that gets the username
   and issuer claims, verifies a role and ensures the account if necessary."
  [secret leeway {:keys [no-val?] :as no-val-opts}]
  (interceptor
   {:name ::validate-jwt
    :enter
    (fn validate-jwt [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            token  (-> ctx
                       (get-in [:request :headers "authorization"])
                       admin-u/header->jwt)
            result (if no-val?
                     (validate-no-val-jwt lrs token no-val-opts)
                     (validate-jwt* lrs token secret leeway))]
        (cond
          ;; Success - assoc the account ID as an intermediate value
          (map? result)
          (-> ctx
              (assoc-in [::data] result)
              (assoc-in [:request :session ::data] result))
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

(defn validate-one-time-jwt
  "Validate one-time JWTs. Checks that they are not expired and are signed
   properly just like regular JWTs, then automatically revoke them."
  [secret leeway]
  (interceptor
   {:name ::validate-one-time-jwt
    :enter
    (fn validate-one-time-jwt [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            token  (get-in ctx [:request :params :token])
            result (validate-jwt* lrs token secret leeway)]
        (cond
          (or (= :lrsql.admin/unauthorized-token-error result)
              (nil? (:one-time-id result)))
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Unauthorized JSON Web Token!"}})
          (map? result)
          (let [{:keys [one-time-id]} result
                block-result
                (adp/-block-one-time-jwt lrs token one-time-id)]
            (if-some [_ (:error block-result)]
              (assoc (chain/terminate ctx)
                     :response
                     {:status 401
                      :body   {:error "Unauthorized, JSON Web Token was not issued!"}})
              ;; Success!
              (-> ctx ; So far :form-params and :edn-params are not implemented
                  (update-in [:request :params] dissoc :token)
                  (update-in [:request :query-params] dissoc :token)
                  (update-in [:request :json-params] dissoc :token)
                  (assoc-in [::data] result)
                  (assoc-in [:request :session ::data] result)))))))}))

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
