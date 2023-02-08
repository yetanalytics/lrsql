(ns lrsql.admin.interceptors.status
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [lrsql.admin.protocol :as adp]))

(def get-status
  "Return LRS status information for visualization in the UI"
  (interceptor
   {:name ::get-status
    :enter
    (fn get-status [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username password]} ::data}
            ctx]
        (assoc ctx
               :response
               {:status 200
                :body   (adp/-get-status lrs {})})))}))
