(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors :as li]))

;; TODO: Add additional interceptors
(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [routes]
  (cset/merge
   routes
   #{;; Create new account
     ["/admin/account" :post (conj i/common-interceptors
                                   li/create-admin)]
     ;; Delete account (and associated tokens)
     ["/admin/account" :delete (conj i/common-interceptors
                                     li/authenticate-admin
                                     li/delete-tokens
                                     li/delete-admin)]
     ;; Create new tokens w/ scope
     ["/admin/token" :put (conj i/common-interceptors
                                li/authenticate-admin
                                li/create-tokens)]
     ;; Create or update new tokens w/ scope
     ["/admin/token" :post (conj i/common-interceptors
                                 li/authenticate-admin
                                 li/update-tokens)]
     ;; Get current tokens + scope associated w/ account
     ["/admin/token" :get (conj i/common-interceptors
                                li/authenticate-admin
                                li/get-tokens)]
     ;; Delete tokens
     ["/admin/token" :delete (conj i/common-interceptors
                                   li/authenticate-admin
                                   li/delete-tokens)]}))
