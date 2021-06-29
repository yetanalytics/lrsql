(ns lrsql.admin.interceptors.account
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.util.admin :as admin-u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-admin-info
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Intermediate Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
