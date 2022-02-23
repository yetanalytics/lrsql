(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [io.pedestal.http :refer [json-body]]
            [io.pedestal.http.body-params :refer [body-params]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.account :as ai]
            [lrsql.admin.interceptors.credentials :as ci]
            [lrsql.admin.interceptors.ui :as ui]
            [lrsql.admin.interceptors.jwt :as ji]
            [lrsql.util.interceptor :as util-i]))

(defn- make-common-interceptors
  [lrs]
  [i/error-interceptor
   (util-i/handle-json-parse-exn true)
   i/x-forwarded-for-interceptor
   json-body
   (body-params)
   (i/lrs-interceptor lrs)])

(defn admin-account-routes
  [common-interceptors jwt-secret jwt-exp jwt-leeway]
  #{;; Log into an existing account
    ["/admin/account/login" :post (conj common-interceptors
                                        ai/validate-params
                                        ai/authenticate-admin
                                        (ai/generate-jwt jwt-secret jwt-exp))
     :route-name :lrsql.admin.account/login]
    ;; Create new account
    ["/admin/account/create" :post (conj common-interceptors
                                         ai/validate-params
                                         (ji/validate-jwt jwt-secret jwt-leeway)
                                         ji/validate-jwt-account
                                         ai/create-admin)
     :route-name :lrsql.admin.account/create]
    ;; Get all accounts
    ["/admin/account" :get (conj common-interceptors
                                 (ji/validate-jwt jwt-secret jwt-leeway)
                                 ji/validate-jwt-account
                                 ai/get-accounts)
     :route-name :lrsql.admin.account/get]
    ;; Delete account (and associated credentials)
    ["/admin/account" :delete (conj common-interceptors
                                    ai/validate-delete-params
                                    (ji/validate-jwt jwt-secret jwt-leeway)
                                    ji/validate-jwt-account
                                    ai/delete-admin)
     :route-name :lrsql.admin.account/delete]})

(defn admin-cred-routes
  [common-interceptors jwt-secret jwt-leeway]
  #{;; Create new API key pair w/ scope set
    ["/admin/creds" :post (conj common-interceptors
                                (ci/validate-params {:scopes? true})
                                (ji/validate-jwt jwt-secret jwt-leeway)
                                ji/validate-jwt-account
                                ci/create-api-keys)
     :route-name :lrsql.admin.creds/put]
    ;; Create or update new keys w/ scope set
    ["/admin/creds" :put (conj common-interceptors
                               (ci/validate-params {:key-pair? true
                                                    :scopes?   true})
                               (ji/validate-jwt jwt-secret jwt-leeway)
                               ji/validate-jwt-account
                               ci/update-api-keys)
     :route-name :lrsql.admin.creds/post]
    ;; Get current keys + scopes associated w/ account
    ["/admin/creds" :get (conj common-interceptors
                               (ji/validate-jwt jwt-secret jwt-leeway)
                               ji/validate-jwt-account
                               ci/read-api-keys)
     :route-name :lrsql.admin.creds/get]
    ;; Delete API key pair and associated scopes
    ["/admin/creds" :delete (conj common-interceptors
                                  (ci/validate-params {:key-pair? true})
                                  (ji/validate-jwt jwt-secret jwt-leeway)
                                  ji/validate-jwt-account
                                  ci/delete-api-keys)
     :route-name :lrsql.admin.creds/delete]})

(defn admin-ui-routes
  [common-interceptors]
  #{;; Redirect root to admin UI
    ["/" :get `ui/admin-ui-redirect
     :route-name :lrsql.admin.ui/root-redirect]
    ;; Redirect admin w/o slash to admin UI
    ["/admin" :get `ui/admin-ui-redirect
     :route-name :lrsql.admin.ui/path-redirect]
    ;; Redirect admin with slash to admin UI
    ["/admin/" :get `ui/admin-ui-redirect
     :route-name :lrsql.admin.ui/slash-redirect]
    ["/admin/env" :get (conj common-interceptors
                             ui/get-env)
     :route-name :lrsql.admin.ui/get-env]})

(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [{:keys [lrs
           exp
           leeway
           secret
           enable-admin-ui
           oidc-interceptors]
    :or {oidc-interceptors []}}
   routes]
  (let [common-interceptors (make-common-interceptors lrs)
        common-interceptors-oidc (into common-interceptors)]
    (cset/union routes
                (admin-account-routes common-interceptors-oidc secret exp leeway)
                (admin-cred-routes common-interceptors-oidc secret leeway)
                (when enable-admin-ui
                  (admin-ui-routes common-interceptors)))))
