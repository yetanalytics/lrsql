(ns lrsql.admin.interceptors.lrs-management
  (:require [clojure.spec.alpha :as s]
            [clojure.edn        :as edn]
            [clojure.java.io    :as io]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin     :as ads]
            [lrsql.util.admin     :as admin-u]
            [lrsql.admin.interceptors.jwt :as jwt]
            [com.yetanalytics.lrs.pedestal.interceptor.xapi :as i-xapi]
            [com.yetanalytics.lrs-reactions.spec :as rs])
  (:import [javax.servlet ServletOutputStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor Delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-delete-actor-params
  (interceptor
   {:name ::validate-delete-actor-params
    :enter (fn validate-delete-params [ctx]
             (let [params (get-in ctx [:request :json-params])]
               (if-some [err (s/explain-data
                              ads/delete-actor-spec 
                               params)]
                 (assoc (chain/terminate ctx)
                        :response
                        {:status 400
                         :body {:error (format "Invalid parameters:\n%s"
                                               (-> err s/explain-out with-out-str))}})
                 (assoc ctx ::data params))))}))

(def delete-actor
  (interceptor
   {:name ::delete-actor
    :enter (fn delete-actor [ctx]
             (let [{lrs :com.yetanalytics/lrs
                    params ::data}
                   ctx]
               (adp/-delete-actor lrs params)
               (assoc ctx
                      :response {:status 200
                                 :body params})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSV Download
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; See also: `admin.interceptors.account/generate-jwt`
(defn generate-one-time-jwt
  [secret exp]
  (interceptor
   {:name ::convert-jwt
    :enter
    (fn convert-jwt [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id] :as jwt-claim} ::jwt/data}
            ctx
            {new-jwt :jwt exp :exp one-time-id :oti}
            (admin-u/one-time-jwt jwt-claim secret exp)]
        (adp/-create-one-time-jwt lrs new-jwt exp one-time-id)
        (assoc (chain/terminate ctx)
               :response
               {:status 200
                :body   {:account-id     account-id
                         :json-web-token new-jwt}})))}))

(def validate-property-paths
  (interceptor
   {:name ::validate-property-paths
    :enter
    (fn validate-property-paths [ctx]
      (let [property-paths (-> ctx
                               (get-in [:request
                                        :params
                                        :property-paths])
                               edn/read-string)]
        (if-some [e (s/explain-data (s/every ::rs/path) property-paths)]
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body {:error (format "Invalid property paths:\n%s"
                                        (-> e s/explain-out with-out-str))}})
          ;; Need to dissoc since lrs.pedestal.interceptor.xapi/params-interceptor
          ;; restricts allowed keys in the query param map.
          (-> ctx
              (update-in [:request :params] dissoc :property-paths)
              (update-in [:request :query-params] dissoc :property-paths)
              (assoc-in [:request :property-paths] property-paths)))))}))

(def validate-query-params
  (interceptor
   (i-xapi/params-interceptor :xapi.statements.GET.request/params)))

(def csv-response-header
  {"Content-Type"        "text/csv"
   "Content-Disposition" "attachment"})

(def download-statement-csv
  (interceptor
   {:name ::download-statement-csv
    :enter
    (fn download-statement-csv [ctx]
      (let [{lrs :com.yetanalytics/lrs
             request :request}
            ctx
            {:keys [property-paths query-params]}
            request]
        (assoc ctx
               :response
               {:status  200
                :headers csv-response-header
                :body    (fn [^ServletOutputStream os]
                           (with-open [writer (io/writer os)]
                             (adp/-get-statements-csv lrs
                                                      writer
                                                      property-paths
                                                      query-params)))})))}))
