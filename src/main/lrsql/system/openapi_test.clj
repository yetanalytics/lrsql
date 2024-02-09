(ns lrsql.system.open-api-test
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.actor :as a-util]
            [lrsql.sqlite.record :as r]
            [lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]]
            [com.yetanalytics.gen-openapi.splice :as splice]
            [com.yetanalytics.gen-openapi.generate :as generate]
            [com.yetanalytics.gen-openapi.core :as goa]))

(def sys (system/system (r/map->SQLiteBackend {}) :test-sqlite-mem))
(def sys' (component/start sys))

(def lrs (:lrs sys'))
(def ds (-> sys' :lrs :connection :conn-pool))

(defn get-routes [] (::http/routes  (:service (:webserver sys'))))

(def lrsql-route-paths #{"/admin/account/login"
                         "/admin/account/create"
                         "/admin/account/password"
                         "/admin/account"
                         "/admin/me"
                         "/admin/creds"
                         "/admin/status"})

(defn gros                              ;get routes of set
  [r s]
  (filter #(s (first %)) r))

(defn lrsql-routes []
  (gros (get-routes) lrsql-route-paths))


(component/stop sys)


