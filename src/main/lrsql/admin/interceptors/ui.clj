(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]))

(defn admin-ui-redirect
  "Handler function to redirect to the admin ui"
  [_]
  (resp/redirect "/admin/index.html"))

(def get-env
  "Provide select config data to client upon request"
  (interceptor
   {:name ::get-env
    :enter
    (fn get-env [ctx]
      (let [{url-prefix ::i/path-prefix
             enable-stmt-html ::i/statement-html?} ctx]
        (assoc ctx :response
               {:status 200 :body {:url-prefix url-prefix
                                   :enable-stmt-html (some? enable-stmt-html)}})))}))
