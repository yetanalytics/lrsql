(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [io.pedestal.interceptor :refer [interceptor]]))

(defn admin-ui-redirect
  "Handler function to redirect to the admin ui"
  [_]
  (resp/redirect "/admin/index.html"))

(defn get-env
  "Provide select config data to client upon request"
  [enable-stmt-html url-prefix]
  (interceptor
   {:name ::get-env
    :enter
    (fn get-env [ctx]
      (assoc ctx :response
             {:status 200 :body {:urlPrefix      url-prefix
                                 :enableStmtHtml enable-stmt-html}}))}))
