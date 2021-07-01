(ns lrsql.admin.interceptors.credentials
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.auth :as as]
            [lrsql.util.admin :as admin-u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn validate-params
  "Validate that the JSON params include `api-key`, `secret-key`, and/or
   `scopes`, depending on if `key-pair?` and `scopes?` are true or not."
  [{:keys [key-pair? scopes?]}]
  {:name ::validate-params
   :enter
   (fn validate-params [ctx]
     (let [params (get-in ctx [:request :json-params])]
       (if-some [err (or (when key-pair?
                           (s/explain-data as/key-pair-spec params))
                         (when scopes?
                           (s/explain-data as/scopes-spec params)))]
         (assoc (chain/terminate ctx)
                :response
                {:status 400
                 :body (format "Invalid parameters:\n%s"
                               (-> err s/explain-out with-out-str))})
         (let [cred-info (select-keys params [:api-key
                                              :secret-key
                                              :scopes])]
           (-> ctx
               (assoc ::data cred-info)
               (assoc-in [:request :session ::data] cred-info))))))})

(defn validate-jwt
  "Validate that the header JWT is valid (e.g. not expired)."
  [leeway]
  (interceptor
   {:name ::validate-jwt
    :enter
    (fn validate-jwt [ctx]
      (let [token  (-> ctx
                       (get-in [:request :headers "authorization"])
                       admin-u/header->jwt)
            result (admin-u/jwt->account-id token leeway)]
        (cond
          ;; Success - assoc the account ID as an intermediate value
          (uuid? result)
          (-> ctx
              (assoc-in [::data :account-id] result)
              (assoc-in [:request :session ::data :account-id] result))

          ;; Failure - the token has expired
          (= :lrsql.admin/expired-token-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401 :body "Expired token!"})

          ;; Failure - the token is invalid
          (= :lrsql.admin/invalid-token-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400 :body "Missing or invalid token!"}))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-api-keys
  (interceptor
   {:name ::create-api-keys
    :enter
    (fn create-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id scopes]} ::data}
            ctx
            api-key-res
            (adp/-create-api-keys lrs
                                  account-id
                                  (set scopes))]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def read-api-keys
  (interceptor
   {:name ::read-api-keys
    :enter
    (fn read-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::data}
            ctx
            api-key-res
            (adp/-get-api-keys lrs
                               account-id)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def update-api-keys
  (interceptor
   {:name ::update-api-keys
    :enter
    (fn update-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id api-key secret-key scopes]} ::data}
            ctx
            api-key-res
            (adp/-update-api-keys lrs
                                  account-id
                                  api-key
                                  secret-key
                                  (set scopes))]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def delete-api-keys
  (interceptor
   {:name ::delete-api-keys
    :enter
    (fn delete-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id api-key secret-key]} ::data}
            ctx
            api-key-res
            (adp/-delete-api-keys lrs
                                  account-id
                                  api-key
                                  secret-key)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))
