(ns lrsql.admin.interceptors.status
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin.status :as adss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-params
  "Validate the JSON params for admin status."
  (interceptor
   {:name ::validate-params
    :enter
    (fn validate-params [ctx]
      (let [params (get-in ctx [:request :query-params] {})]
        (if-some [err (s/explain-data adss/get-status-params-spec params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (assoc ctx ::params params))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-status
  "Return LRS status information for visualization in the UI"
  (interceptor
   {:name ::get-status
    :enter
    (fn get-status [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx]
        (assoc ctx
               :response
               {:status 200
                :body   (adp/-get-status lrs (::params ctx))})))}))
