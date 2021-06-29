(ns lrsql.admin.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as admin-u]
            [lrsql.util.auth :as auth-u]))

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
        (if-some [emsg
                  (cond
                    (nil? ?username) "Missing username in body!"
                    (nil? ?password) "Missing password in body!"
                    (not (string? ?username)) "Username is not string!"
                    (not (string? ?password)) "Password is not string!"
                    :else nil)]
          (assoc (chain/terminate ctx) :response {:status 400 :body emsg})
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
          ;; Pass it along as an intermediate value
          (uuid? result)
          (assoc-in ctx
                    [:request :json-params :account-id]
                    result)

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
          ;; Pass it along as an intermediate value
          (uuid? result)
          (assoc-in ctx
                    [:request :json-params :account-id]
                    result)

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
      (let [{:keys [account-id username]} ; From `authenticate-admin`
            (get-in ctx [:request :json-params])] 
        (adp/-delete-account lrs account-id)
        (assoc ctx
               :response
               {:status 200
                :body   (format "Successfully deleted account \"%s\"!"
                                username)})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Web Tokens
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-jwt
  [exp]
  (interceptor
   {:name ::generate-jwt
    :enter
    (fn generate-jwt [ctx]
      (let [{:keys [account-id]} (get-in ctx [:request :json-params])
            json-web-token       (admin-u/account-id->jwt account-id exp)]
        (assoc ctx
               :response
               {:status 200
                :body   {:account-id     account-id
                         :json-web-token json-web-token}})))}))

(defn validate-jwt
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
          ;; Success - pass the account ID in the request body
          (uuid? result)
          (assoc-in ctx
                    [:request :json-params :account-id]
                    result)

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

(def validate-key-pair
  (interceptor
   {:name ::validate-key-pair
    :enter
    (fn validate-key-pair [ctx]
      (let [{?api-key :api-key ?secret-key :secret-key}
            (get-in ctx [:request :json-params])]
        (if-some [emsg (cond
                         (nil? ?api-key)
                         "API key is not present!"
                         (nil? ?secret-key)
                         "Secret key is not present!"
                         (not (string? ?api-key))
                         "API key is not a string!"
                         (not (string? ?secret-key))
                         "Secret key is not present"
                         (not (re-matches Base64RegEx ?api-key))
                         "API key is not in Base64 format!"
                         (not (re-matches Base64RegEx ?secret-key))
                         "Secret key is not in Base64 format!"
                         :else
                         nil)]
          (assoc (chain/terminate ctx) :response {:status 400 :body emsg})
          ctx)))}))

(def validate-scopes
  (interceptor
   {:name ::validate-scopes
    :enter
    (fn validate-scopes [ctx]
      (let [{?scopes :scopes} (get-in ctx [:request :json-params])]
        (if-some [emsg
                  (cond
                    (nil? ?scopes)
                    "Scopes are not present!"
                    (not (every? (partial contains? auth-u/scope-str-kw-map)
                                 ?scopes))
                    "Invalid scopes are present!")]
          (assoc (chain/terminate ctx) :response {:status 400 :body emsg})
          ctx)))}))

(def create-api-keys
  (interceptor
   {:name ::create-api-keys
    :enter
    (fn create-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id scopes]} (get-in ctx [:request :json-params])
            scope-set   (set scopes)
            api-key-res (adp/-create-api-keys lrs account-id scope-set)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def read-api-keys
  (interceptor
   {:name ::read-api-keys
    :enter
    (fn read-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id]} (get-in ctx [:request :json-params])
            api-key-res          (adp/-get-api-keys lrs account-id)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def update-api-keys
  (interceptor
   {:name ::update-api-keys
    :enter
    (fn update-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id api-key secret-key scopes]}
            (get-in ctx [:request :json-params])
            scope-set
            (set scopes)
            api-key-res
            (adp/-update-api-keys lrs account-id api-key secret-key scope-set)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def delete-api-keys
  (interceptor
   {:name ::delete-api-keys
    :enter
    (fn delete-api-keys [{lrs :com.yetanalytics/lrs :as ctx}]
      (let [{:keys [account-id api-key secret-key]}
            (get-in ctx [:request :json-params])
            api-key-res
            (adp/-delete-api-keys lrs account-id api-key secret-key)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))
