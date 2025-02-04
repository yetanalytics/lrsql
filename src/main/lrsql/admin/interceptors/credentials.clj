(ns lrsql.admin.interceptors.credentials
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.admin.interceptors.jwt :as jwt]
            [lrsql.spec.auth :as as]))

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
                           (s/explain-data as/scopes-spec params))
                         (when (:label params)
                           (s/explain-data as/label-spec params)))]
         ;; Invalid parameters - Bad Request
         (assoc (chain/terminate ctx)
                :response
                {:status 400
                 :body   {:error (format "Invalid parameters:\n%s"
                                         (-> err s/explain-out with-out-str))}})
         ;; Valid parameters - continue
         (let [cred-info (select-keys params [:api-key
                                              :secret-key
                                              :label
                                              :scopes])]
           (-> ctx
               (assoc ::data cred-info)
               (assoc-in [:request :session ::data] cred-info))))))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-api-keys
  "Create a new pair of API keys and store them in the credential table."
  (interceptor
   {:name ::create-api-keys
    :enter
    (fn create-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::jwt/data
             {:keys [label scopes]} ::data}
            ctx
            api-key-res
            (adp/-create-api-keys lrs
                                  account-id
                                  label
                                  (set scopes))]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def read-api-keys
  "Read the API keys associated with an account ID."
  (interceptor
   {:name ::read-api-keys
    :enter
    (fn read-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::jwt/data}
            ctx
            api-key-res
            (adp/-get-api-keys lrs
                               account-id)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def update-api-keys
  "Update the scopes of the selected API key pair."
  (interceptor
   {:name ::update-api-keys
    :enter
    (fn update-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::jwt/data
             {:keys [api-key secret-key label scopes]} ::data}
            ctx
            api-key-res
            (adp/-update-api-keys lrs
                                  account-id
                                  api-key
                                  secret-key
                                  label
                                  (set scopes))]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))

(def delete-api-keys
  "Delete the selected API key pair."
  (interceptor
   {:name ::delete-api-keys
    :enter
    (fn delete-api-keys [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::jwt/data
             {:keys [api-key secret-key]} ::data}
            ctx
            api-key-res
            (adp/-delete-api-keys lrs
                                  account-id
                                  api-key
                                  secret-key)]
        (assoc ctx
               :response
               {:status 200 :body api-key-res})))}))
