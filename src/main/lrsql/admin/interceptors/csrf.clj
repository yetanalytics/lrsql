(ns lrsql.admin.interceptors.csrf
  (:require 
   [io.pedestal.interceptor :refer [interceptor]]
   [io.pedestal.interceptor.chain :as chain]))

(def csrf-check-interceptor
  (interceptor
   {:name ::check-csrf
    :enter
    (fn check-csrf [ctx]
      (if (or
           (= :get (get-in ctx [:request :request-method]))
           (contains? (get-in ctx [:request :headers ])
                      "x-csrf-dummy"))
        ctx
        (assoc (chain/terminate ctx)
               :response
               {:status 403
                :body   {:error "Possible CSRF detected."}})))}))
