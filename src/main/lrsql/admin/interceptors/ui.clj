(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.oidc :as oidc-i]))

(defn admin-ui-redirect
  "Handler function to redirect to the admin ui"
  [path-prefix]
  (fn [_]
    (resp/redirect (str path-prefix "/admin/index.html"))))

(defn get-env
  "Provide select config data to client upon request. Takes a map with static
  config to inject:
    :enable-admin-status - boolean, determines if the admin status endpoint is
      enabled."
  [{:keys [enable-admin-delete-actor enable-admin-status enable-reactions no-val? proxy-path]
    :or   {enable-admin-delete-actor false
           enable-admin-status false
           enable-reactions    false
           no-val?             false}}]
  (interceptor
   {:name ::get-env
    :enter
    (fn get-env [ctx]
      (let [{url-prefix       ::i/path-prefix
             enable-stmt-html ::i/statement-html?
             oidc-env         ::oidc-i/admin-env} ctx]
        (assoc ctx
               :response
               {:status 200
                :body
                (merge
                 {:url-prefix                url-prefix
                  :proxy-path                proxy-path
                  :enable-stmt-html          (some? enable-stmt-html)
                  :enable-admin-delete-actor enable-admin-delete-actor
                  :enable-admin-status       enable-admin-status
                  :enable-reactions          enable-reactions
                  :no-val?                   no-val?}
                 oidc-env)})))}))
