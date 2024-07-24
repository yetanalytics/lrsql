(ns lrsql.admin.interceptors.lrs-management
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin :as ads]
            [com.yetanalytics.gen-openapi.core :as oa-core]
            [com.yetanalytics.gen-openapi.generate.schema :as gs]
            [com.yetanalytics.lrs.pedestal.openapi :as lrs-oa]))

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

(defn openapi [routes version]
  (interceptor
   {:name ::openapi
    :enter (fn openapi [ctx]
             (assoc ctx :response
                    {:status 200
                     :body (oa-core/make-oa-map
                            {:openapi "3.0.0"
                             :info {:title "LRSQL"
                                    :version version}
                             :externalDocs {:url "https://github.com/yetanalytics/lrsql/blob/main/doc/endpoints.md"}
                             :components (gs/dsl lrs-oa/components)}
                            routes)}))}))
