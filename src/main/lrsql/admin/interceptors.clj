(ns lrsql.admin.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as au]))

;; TODO: Expand current placeholder interceptors

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Accounts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def verify-admin-info
  (interceptor
   {:name ::verify-admin-info
    :enter
    (fn verify-admin-info [ctx]
      (let [{?username :username ?password :password}
            (get-in ctx [:request :json-params])]
        (cond
          ;; Missing username or password
          (or (nil? ?username) (nil? ?password))
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400 :body "Missing username or password in body!"})
          
          ;; Non-string username or password
          (or (not (string? ?username)) (not (string? ?password)))
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400 :body "Username or password must be string!"})
          
          ;; We're good
          :else
          ctx)))}))

(def create-admin
  (interceptor
   {:name ::create-admin
    :enter
    (fn create-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [username password]}
            (get-in ctx [:request :json-params])
            {:keys [result]}
            (adp/-create-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id result}})

          ;; The account already exists
          (= :lrsql.admin/existing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 409 :body (format "An account \"%s\" already exists!"
                                            username)}))))}))

(def authenticate-admin
  (interceptor
   {:name ::authenticate-admin
    :enter
    (fn authenticate-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [username password]}
            (get-in ctx [:request :json-params]) 
            {:keys [result]}
            (adp/-authenticate-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          (uuid? result) 
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id result}})
          
          ;; The account cannot be found
          (= :lrsql.admin/missing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404 :body (format "Account \"%s\" not found!"
                                            username)})
          
          ;; The password was invalid
          (= :lrsql.admin/invalid-password-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401 :body (format "Incorrect password for \"%s\"!"
                                            username)}))))}))

(def delete-admin
  (interceptor
   {:name ::delete-admin
    :enter
    (fn delete-admin [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [username]}
            (get-in ctx [:request :json-params])
            {:keys [account-id]}
            (get-in ctx [:response :body])] ; From `authenticate-admin`
        (adp/-delete-account lrs account-id)
        (assoc ctx
               :response
               {:status 200 :body (format "Successfully deleted \"%s\"!"
                                          username)})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Web Tokens
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def generate-jwt
  (interceptor
   {:name ::generate-jwt
    :enter
    (fn generate-jwt [ctx]
      (let [{:keys [account-id]} (get-in ctx [:response :body])
            json-web-token       (au/account-id->jwt account-id)]
        (assoc-in ctx [:response :body :jwt] json-web-token)))}))

(def validate-jwt
  (interceptor
   {:name ::validate-jwt
    :enter
    (fn validate-jwt [ctx]
      (let [{tok :token} (get-in ctx [:header :token])
            result       (au/jwt->account-id tok)]
        (cond
          ;; Success - pass the account ID in the body
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id result}})

          ;; Failure - the token has expired
          (= :lrsql.admin/expired-token-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401 :body "Expired token!"})

          ;; Failure - the token is invalid
          (= :lrsql.admin/invalid-token-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400 :body "Invalid, missing, or malformed token!"}))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Keys
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-api-keys
  (interceptor
   {:name ::create-api-keys
    :enter
    (fn create-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]} (get-in ctx [:response :body])
            {:keys [scopes]}     (get-in ctx [:request :json-params])
            api-key-res          (adp/-create-api-keys lrs account-id scopes)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def read-api-keys
  (interceptor
   {:name ::read-api-keys
    :enter
    (fn read-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]} (get-in ctx [:response :body])
            api-key-res          (adp/-get-api-keys lrs account-id)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def update-api-keys
  (interceptor
   {:name ::update-api-keys
    :enter
    (fn update-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]}
            (get-in ctx [:response :body])
            {:keys [api-key secret-key scopes]}
            (get-in ctx [:request :json-params])
            api-key-res
            (adp/-update-api-keys lrs account-id api-key secret-key scopes)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def delete-api-keys
  (interceptor
   {:name ::delete-api-keys
    :enter
    (fn delete-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]}
            (get-in ctx [:response :body])
            {:keys [api-key secret-key]}
            (get-in ctx [:request :json-params])
            api-key-res
            (adp/-delete-api-keys lrs account-id api-key secret-key)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))
