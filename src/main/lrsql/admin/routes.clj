(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [io.pedestal.http :refer [json-body]]
            [io.pedestal.http.body-params :refer [body-params]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.account :as ai]
            [lrsql.admin.interceptors.credentials :as ci]))

(defn- make-common-interceptors
  [lrs]
  [i/x-forwarded-for-interceptor
   json-body
   i/error-interceptor
   (body-params)
   (i/lrs-interceptor lrs)])

(defn admin-account-routes
  [common-interceptors jwt-exp]
  #{;; Create new account
    ["/admin/account/create" :post (conj common-interceptors
                                         ai/validate-params
                                         ai/create-admin
                                         (ai/generate-jwt jwt-exp))
     :route-name :lrsql.admin.account/create]
    ;; Log into an existing account
    ["/admin/account/login" :post (conj common-interceptors
                                        ai/validate-params
                                        ai/authenticate-admin
                                        (ai/generate-jwt jwt-exp))
     :route-name :lrsql.admin.account/login]
    ;; Delete account (and associated credentials)
    ["/admin/account" :delete (conj common-interceptors
                                    ai/validate-params
                                    ai/authenticate-admin
                                    ai/delete-admin)
     :route-name :lrsql.admin.account/delete]})

(defn admin-cred-routes
  [common-interceptors jwt-leeway]
  #{;; Create new API key pair w/ scope set
    ["/admin/creds" :put (conj common-interceptors
                               (ci/validate-params {:scopes? true})
                               (ci/validate-jwt jwt-leeway)
                               ci/create-api-keys)
     :route-name :lrsql.admin.creds/put]
    ;; Create or update new keys w/ scope set
    ["/admin/creds" :post (conj common-interceptors
                                (ci/validate-params {:key-pair? true
                                                     :scopes?   true})
                                (ci/validate-jwt jwt-leeway)
                                ci/update-api-keys)
     :route-name :lrsql.admin.creds/post]
    ;; Get current keys + scopes associated w/ account
    ["/admin/creds" :get (conj common-interceptors
                               (ci/validate-jwt jwt-leeway)
                               ci/read-api-keys)
     :route-name :lrsql.admin.creds/get]
    ;; Delete API key pair and associated scopes
    ["/admin/creds" :delete (conj common-interceptors
                                  (ci/validate-params {:key-pair? true})
                                  (ci/validate-jwt jwt-leeway)
                                  ci/delete-api-keys)
     :route-name :lrsql.admin.creds/delete]})

(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [{:keys [lrs exp leeway]} routes]
  (let [common-interceptors (make-common-interceptors lrs)]
    (cset/union routes
                (admin-account-routes common-interceptors exp)
                (admin-cred-routes common-interceptors leeway))))
