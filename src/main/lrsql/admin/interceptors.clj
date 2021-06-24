(ns lrsql.admin.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as au]))

;; TODO: Expand current placeholder interceptors

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Accounts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-admin
  (interceptor
   {:name ::create-admin
    :enter
    (fn create-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [username password]}
            (get-in ctx [:request :params :body])
            {:keys [result]}
            (adp/-create-account lrs username password)]
        (cond
          (uuid? result) ; The result is the account ID
          (assoc-in ctx
                    [:request :params :body :account-id]
                    result)
          (= :lrsql.admin/existing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 409 :body "ADMIN ACCOUNT CONFLICT"}))))}))

(def authenticate-admin
  (interceptor
   {:name ::authenticate-admin
    :enter
    (fn authenticate-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [username password]}
            (get-in ctx [:request :params :body]) 
            {:keys [result]}
            (adp/-authenticate-account lrs username password)]
        (cond
          (uuid? result) ; The result is the account ID
          (assoc-in ctx
                    [:request :params :body :account-id]
                    result)
          (= :lrsql.admin/missing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404 :body "ADMIN ACCOUNT NOT FOUND"})
          (= :lrsql.admin/invalid-password-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401 :body "ADMIN ACCOUNT FORBIDDEN"}))))}))

(def delete-admin
  (interceptor
   {:name ::delete-admin
    :enter
    (fn delete-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]}
            (get-in ctx [:request :params :body])]
        (adp/-delete-account lrs account-id)
        ctx))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Web Tokens
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def generate-jwt
  (interceptor
   {:name ::generate-jwt
    :enter
    (fn generate-jwt [ctx]
      (let [{:keys [account-id]} (get-in ctx [:request :params :body])
            json-web-token     (au/account-id->jwt account-id)]
        (assoc ctx
               :response
               {:status 200 :body json-web-token})))}))

(def validate-jwt
  (interceptor
   {:name ::validate-jwt
    :enter
    (fn validate-jwt [ctx]
      (let [{tok :token} (get-in ctx [:header :token])]
        (if-some [account-id (au/jwt->account-id tok)]
          (assoc-in ctx [:request :params :body :account-id] account-id)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401 :body "INVALID TOKEN FORBIDDEN"}))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Keys
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-api-keys
  (interceptor
   {:name ::create-api-keys
    :enter
    (fn create-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id scopes]}
            (get-in ctx [:request :params :body])
            api-key-res
            (adp/-create-api-keys lrs account-id scopes)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def read-api-keys
  (interceptor
   {:name ::read-api-keys
    :enter
    (fn read-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]}
            (get-in ctx [:request :params :body])
            api-key-res
            (adp/-get-api-keys lrs account-id)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def update-api-keys
  (interceptor
   {:name ::update-api-keys
    :enter
    (fn update-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id key-pair scopes]}
            (get-in ctx [:request :params :body])
            api-key-res
            (adp/-update-api-keys lrs account-id key-pair scopes)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def delete-api-keys
  (interceptor
   {:name ::delete-api-keys
    :enter
    (fn delete-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id key-pair]}
            (get-in ctx [:request :params :body])
            api-key-res
            (adp/-delete-api-keys lrs account-id key-pair)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))
