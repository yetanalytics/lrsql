(ns lrsql.admin.interceptors.delete-actor
  (:require [clojure.spec.alpha :as s]
            [lrsql.ops.command.statement :as stmt-cmd]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.actor :as as]))

(def validate-delete-actor-params
  (interceptor {:name ::validate-delete-actor-params
                :enter (fn validate-delete-params [ctx]
                         (let [params (get-in ctx [:request :json-params])]
                           (if-some [err (s/explain-data (s/keys :req-un [:lrsql.spec.actor/actor-ifi]) params)]
                             (assoc (chain/terminate ctx)
                                    :response
                                    {:status 400
                                     :body {:error (format "Invalid parameters:\n%s"
                                                           (-> err s/explain-out with-out-str))}})
                             (assoc ctx :actor-ifi params))))}))

(def delete-actor
  (interceptor
   {:name ::delete-actor
    :enter (fn delete-actor [ctx]
             (let [{lrs :com.yetanalytics/lrs
                    actor-ifi :actor-ifi}
                   ctx]
               (adp/-delete-actor lrs actor-ifi)))}))
