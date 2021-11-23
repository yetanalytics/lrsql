(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [io.pedestal.interceptor :refer [interceptor]]))

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
      (let [{url-prefix :com.yetanalytics.lrs.pedestal.interceptor/path-prefix
             enable-stmt-html :com.yetanalytics.lrs.pedestal.interceptor/statement-html?} ctx]
        (assoc ctx :response
               {:status 200 :body {:urlPrefix url-prefix
                                   :enableStmtHtml (some? enable-stmt-html)}})))}))
