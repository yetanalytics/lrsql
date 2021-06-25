(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [io.pedestal.http :refer [json-body]]
            [io.pedestal.http.body-params :refer [body-params]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors :as li]))

(defn- common-interceptors
  [lrs]
  [i/x-forwarded-for-interceptor
   json-body
  ;;  i/error-interceptor
   (body-params)
   (i/lrs-interceptor lrs)])

(defn admin-account-routes
  [comm-interceptors]
  #{;; Create new account
    ["/admin/account/create" :post (conj comm-interceptors
                                         li/create-admin
                                         li/generate-jwt)
     :route-name :lrsql.admin.account/create]
     ;; Log into an existing account
    ["/admin/account/login" :post (conj comm-interceptors
                                        li/authenticate-admin
                                        li/generate-jwt)
     :route-name :lrsql.admin.account/login]
     ;; Delete account (and associated tokens)
    ["/admin/account" :delete (conj comm-interceptors
                                    li/authenticate-admin
                                    li/delete-admin)
     :route-name :lrsql.admin.account/delete]})

(defn admin-token-routes
  [comm-interceptors]
  #{;; Create new API key pair w/ scope set
    ["/admin/token" :put (conj comm-interceptors
                               li/validate-jwt
                               li/create-api-keys)
     :route-name :lrsql.admin.token/put]
     ;; Create or update new keys w/ scope set
    ["/admin/token" :post (conj comm-interceptors
                                li/validate-jwt
                                li/update-api-keys)
     :route-name :lrsql.admin.token/post]
     ;; Get current keys + scopes associated w/ account
    ["/admin/token" :get (conj comm-interceptors
                               li/validate-jwt
                               li/read-api-keys)
     :route-name :lrsql.admin.token/get]
     ;; Delete API key pair and associated scopes
    ["/admin/token" :delete (conj i/common-interceptors
                                  li/validate-jwt
                                  li/delete-api-keys)
     :route-name :lrsql.admin.token/delete]})

;; TODO: Add additional interceptors
(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [lrs routes]
  (let [common-i (common-interceptors lrs)]
    (cset/union routes
                (admin-account-routes common-i)
                (admin-token-routes common-i))))
