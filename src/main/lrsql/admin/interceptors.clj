(ns lrsql.admin.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]))

;; TODO: Expand current placeholder interceptors

(def create-admin
  (interceptor
   {:name  ::create-admin
    :enter identity}))

(def authenticate-admin
  (interceptor
   {:name  ::authenticate-admin
    :enter identity}))

(def delete-admin
  (interceptor
   {:name  ::delete-admin
    :enter identity}))

(def create-tokens
  (interceptor
   {:name ::create-tokens
    :enter identity}))

(def update-tokens
  (interceptor
   {:name ::update-tokens
    :enter identity}))

(def get-tokens
  (interceptor
   {:name ::get-tokens
    :enter identity}))

(def delete-tokens
  (interceptor
   {:name ::delete-tokens
    :enter identity}))
