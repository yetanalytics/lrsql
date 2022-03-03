(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.oidc :as oidc-i]))

(defn admin-ui-redirect
  "Handler function to redirect to the admin ui"
  [_]
  (resp/redirect "/admin/index.html"))

(defn admin-ui-redirect-with-query
  "Like admin-ui-redirect, but preserves query string."
  [{:keys [query-string]}]
  (resp/redirect
   (str "/admin/index.html"
        (when query-string
          (str "?"
               query-string)))))

(def get-env
  "Provide select config data to client upon request"
  (interceptor
   {:name ::get-env
    :enter
    (fn get-env [ctx]
      (let [{url-prefix          ::i/path-prefix
             enable-stmt-html    ::i/statement-html?
             ?oidc-client-config ::oidc-i/client-config} ctx]
        (assoc ctx
               :response
               {:status 200
                :body
                (cond-> {:url-prefix       url-prefix
                         :enable-stmt-html (some? enable-stmt-html)}
                  ?oidc-client-config
                  (assoc :oidc
                         ?oidc-client-config))})))}))
