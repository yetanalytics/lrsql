(ns lrsql.admin.interceptors.account
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as admin-u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-params
  "Validate that the JSON params contain the params `username` and `password`."
  (interceptor
   {:name ::validate-params
    :enter
    (fn validate-params [ctx]
      (let [params (get-in ctx [:request :json-params])]
        (if-some [err (s/explain-data ads/admin-params-spec params)]
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   (format "Invalid parameters:\n%s"
                                  (-> err s/explain-out with-out-str))})
          (let [acc-info (select-keys params [:username :password])]
            (-> ctx
                (assoc :account-info acc-info)
                (assoc-in [:request :session :account-info] acc-info))))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Intermediate Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-admin
  (interceptor
   {:name ::create-admin
    :enter
    (fn create-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username password]} :account-info}
            ctx
            {:keys [result]}
            (adp/-create-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          ;; Pass it along as an intermediate value
          (uuid? result)
          (-> ctx
              (assoc-in [:account-info :account-id] result)
              (assoc-in [:request :session :account-info :account-id] result))

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
    (fn authenticate-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username password]} :account-info}
            ctx
            {:keys [result]}
            (adp/-authenticate-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          ;; Pass it along as an intermediate value
          (uuid? result)
          (-> ctx
              (assoc-in [:account-info :account-id] result)
              (assoc-in [:request :session :account-info :account-id] result))

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
    (fn delete-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username account-id]} :account-info}
            ctx]
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
      (let [{{:keys [account-id]} :account-info}
            ctx
            json-web-token
            (admin-u/account-id->jwt account-id exp)]
        (assoc ctx
               :response
               {:status 200
                :body   {:account-id     account-id
                         :json-web-token json-web-token}})))}))
