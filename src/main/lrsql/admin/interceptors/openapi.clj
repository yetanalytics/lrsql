(ns lrsql.admin.interceptors.openapi
  (:require [io.pedestal.interceptor :refer [interceptor]]))

(def openapi
  "Returns an openapi spec in json form for lrsql"
  (interceptor
   {:name ::openapi
    :enter
    (fn openapi [_ctx]
      {:status 200
       :body (slurp "/resources/doc/openapi.json")})}))
