(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors :as li]))

(def admin-account-routes
  #{;; Create new account
    ["/admin/account/create" :post (conj i/common-interceptors
                                         i/lrs-interceptor
                                         li/create-admin
                                         li/generate-jwt)
     :route-name :lrsql.admin.account/create]
     ;; Log into an existing account
    ["/admin/account/login" :post (conj i/common-interceptors
                                        i/lrs-interceptor
                                        li/authenticate-admin
                                        li/generate-jwt)
     :route-name :lrsql.admin.account/login]
     ;; Delete account (and associated tokens)
    ["/admin/account" :delete (conj i/common-interceptors
                                    i/lrs-interceptor
                                    li/authenticate-admin
                                    li/delete-admin)
     :route-name :lrsql.admin.account/delete]})

(def admin-token-routes
  #{;; Create new API key pair w/ scope set
    ["/admin/token" :put (conj i/common-interceptors
                               i/lrs-interceptor
                               li/validate-jwt
                               li/create-api-keys)
     :route-name :lrsql.admin.token/put]
     ;; Create or update new keys w/ scope set
    ["/admin/token" :post (conj i/common-interceptors
                                i/lrs-interceptor
                                li/validate-jwt
                                li/update-api-keys)
     :route-name :lrsql.admin.token/post]
     ;; Get current keys + scopes associated w/ account
    ["/admin/token" :get (conj i/common-interceptors
                               i/lrs-interceptor
                               li/validate-jwt
                               li/read-api-keys)
     :route-name :lrsql.admin.token/get]
     ;; Delete API key pair and associated scopes
    ["/admin/token" :delete (conj i/common-interceptors
                                  i/lrs-interceptor
                                  li/validate-jwt
                                  li/delete-api-keys)
     :route-name :lrsql.admin.token/delete]})

;; TODO: Add additional interceptors
(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [routes]
  (cset/union routes admin-account-routes admin-token-routes))
