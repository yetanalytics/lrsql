(ns lrsql.admin.interceptors.openapi
  (:require [com.yetanalytics.gen-openapi.core :as oa-core]
            [com.yetanalytics.gen-openapi.generate.schema :as gs]
            [com.yetanalytics.lrs.pedestal.openapi :as lrs-oa]
            [io.pedestal.interceptor :refer [interceptor]]))

(defn add-lrsql-specifics [lrs-oa-spec]
  (-> lrs-oa-spec
      (update :schemas merge {:KeyPair (gs/o {:api-key :t#string
                                              :secret-key :t#string})
                              :ScopedKeyPair {:allOf [:r#KeyPair
                                                      :r#Scopes]}
                              :Scopes (gs/o {:scopes (gs/a :t#string)})})
      (update :securitySchemes {:bearerAuth {:type :http
                                            :scheme :bearer
                                            :bearerFormat :JWT}})))

(defn openapi [routes version]
  (let [m {:status 200
           :body (oa-core/make-oa-map
                  {:openapi "3.0.0"
                   :info {:title "LRSQL"
                          :version version}
                   :externalDocs {:url "https://github.com/yetanalytics/lrsql/blob/main/doc/endpoints.md"}
                   :components (gs/dsl (add-lrsql-specifics
                                        lrs-oa/components))}
                  routes)}]
    (interceptor
     {:name ::openapi
      :enter (fn openapi [ctx]
               (assoc ctx :response m))})))
