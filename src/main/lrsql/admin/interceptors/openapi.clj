(ns lrsql.admin.interceptors.openapi
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as admin-u]
            [lrsql.admin.interceptors.jwt :as jwt]
            [lrsql.util :as u]))

(def openapi
  "Returns an openapi spec in json form for lrsql"
  (interceptor
   {:name ::openapi
    :enter
    (fn openapi [ctx]
      {:status 200
       :body (slurp "/resources/doc/openapi.json")})}))
