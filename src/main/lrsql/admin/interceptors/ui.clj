(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]
            [selmer.parser :as selm-parser]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.oidc :as oidc-i]
            [lrsql.init.localization :refer [custom-language-map]]))

(defn get-spa
  "Handler function that returns the index.html file."
  [path-prefix]
  (fn [_]
    (-> (selm-parser/render-file "public/admin/index.html"
                                 {:prefix path-prefix})
        resp/response
        (resp/content-type "text/html"))))

(defn admin-ui-redirect
  "Handler function to redirect to the admin UI."
  [path-prefix]
  (fn [_]
    (resp/redirect (str path-prefix "/admin/ui"))))

(defn get-env
  "Provide select config data to client upon request. Takes a map with static
  config to inject:
    :enable-admin-status - boolean, determines if the admin status endpoint is
      enabled."
  [{:keys [jwt-refresh-interval
           jwt-interaction-window
           enable-admin-delete-actor
           enable-admin-status
           admin-language-code
           enable-reactions
           no-val?
           no-val-logout-url
           stmt-get-max
           proxy-path
           auth-by-cred-id]
    :or   {enable-admin-delete-actor false
           enable-admin-status       false
           enable-reactions          false
           no-val?                   false}}]
  (interceptor
   {:name ::get-env
    :enter
    (fn get-env [ctx]
      (let [{url-prefix       ::i/path-prefix
             oidc-env         ::oidc-i/admin-env} ctx]
        (assoc ctx
               :response
               {:status 200
                :body
                (merge
                 (cond-> {:jwt-refresh-interval      jwt-refresh-interval
                          :jwt-interaction-window    jwt-interaction-window
                          :url-prefix                url-prefix
                          :proxy-path                proxy-path
                          :enable-admin-delete-actor enable-admin-delete-actor
                          :enable-admin-status       enable-admin-status
                          :enable-reactions          enable-reactions
                          :no-val?                   no-val?
                          :admin-language-code       admin-language-code
                          :custom-language           (custom-language-map)
                          :stmt-get-max              stmt-get-max
                          :auth-by-cred-id           auth-by-cred-id}
                   (and no-val?
                        (not-empty no-val-logout-url))
                   (assoc :no-val-logout-url no-val-logout-url))
                 oidc-env)})))}))
