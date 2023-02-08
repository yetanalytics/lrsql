(ns lrsql.admin.interceptors.status
  (:require [io.pedestal.interceptor :refer [interceptor]]))

(def get-status
  "Return LRS status information for visualization in the UI"
  (interceptor
   {:name ::get-status
    :enter
    (fn get-status [ctx]
      (assoc ctx
             :response
             {:status 200
              :body   {}}))}))
