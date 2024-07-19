(ns lrsql.system.openapi
  (:require 
   [com.yetanalytics.gen-openapi.generate.schema :as gs]
   [com.yetanalytics.lrs.pedestal.openapi :as lrsoa]))

(def oa-routes (atom nil))
(def general-map {:openapi "3.0.0"
                  :info {:title "LRSQL"
                         :version "0.7.2"}
                  :externalDocs {:url "https://github.com/yetanalytics/lrsql/blob/main/doc/endpoints.md"}
                  :components (gs/dsl lrsoa/components)})

