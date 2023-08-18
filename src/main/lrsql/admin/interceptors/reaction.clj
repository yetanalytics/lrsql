(ns lrsql.admin.interceptors.reaction
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.reaction :as rs]
            [lrsql.util.reaction :as ru]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-create-reaction-params
  "Validate valid params for reaction creation."
  (interceptor
   {:name ::validate-create-reaction-params
    :enter
    (fn validate-params [ctx]
      (let [params (-> (get-in ctx [:request :json-params])
                       (update :ruleset ru/stringify-template))]
        (if-some [err (s/explain-data rs/create-reaction-params-spec params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (assoc ctx ::data params))))}))

(def validate-update-reaction-params
  "Validate valid params for reaction update."
  (interceptor
   {:name ::validate-update-reaction-params
    :enter
    (fn validate-params [ctx]
      (let [{:keys [ruleset] :as raw-params}
            (get-in ctx [:request :json-params])
            params (-> raw-params
                       (update :reaction-id u/str->uuid)
                       (cond->
                         ruleset
                         (update :ruleset ru/stringify-template)))]
        (if-some [err (s/explain-data rs/update-reaction-params-spec params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (assoc ctx ::data params))))}))

(def validate-delete-reaction-params
  "Validate valid params for reaction delete."
  (interceptor
   {:name ::validate-delete-reaction-params
    :enter
    (fn validate-params [ctx]
      (let [params (-> (get-in ctx [:request :json-params])
                       (update :reaction-id u/str->uuid))]
        (if-some [err (s/explain-data rs/delete-reaction-params-spec params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (assoc ctx ::data params))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-reaction
  "Create a new reaction and store it."
  (interceptor
   {:name ::create-reaction
    :enter
    (fn create-reaction [ctx]
      (let [{lrs                      :com.yetanalytics/lrs
             {:keys [ruleset active]} ::data}
            ctx
            {:keys [result]}
            (adp/-create-reaction lrs ruleset active)]
        (assoc ctx
               :response
               {:status 200 :body {:reaction-id result}})))}))

(def get-all-reactions
  "List all reactions."
  (interceptor
   {:name ::get-all-reactions
    :enter
    (fn get-all-reactions [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            result
            (adp/-get-all-reactions lrs)]
        (assoc ctx
               :response
               {:status 200 :body {:reactions
                                   (map
                                    (fn [reaction-record]
                                      (-> reaction-record
                                          (update :created u/time->str)
                                          (update :modified u/time->str)))
                                    result)}})))}))

(def update-reaction
  "Update an existing reaction."
  (interceptor
   {:name ::update-reaction
    :enter
    (fn create-reaction [ctx]
      (let [{lrs                                  :com.yetanalytics/lrs
             {:keys [reaction-id ruleset active]} ::data}
            ctx
            {:keys [result]}
            (adp/-update-reaction lrs reaction-id ruleset active)]
        (cond
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:reaction-id result}})
          (= :lrsql.reaction/reaction-not-found-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404
                  :body   {:error (format "The reaction \"%s\" does not exist!"
                                          (u/uuid->str reaction-id))}}))))}))

(def delete-reaction
  "Delete a reaction."
  (interceptor
   {:name ::delete-reaction
    :enter
    (fn delete-reaction [ctx]
      (let [{lrs                   :com.yetanalytics/lrs
             {:keys [reaction-id]} ::data}
            ctx
            {:keys [result]}
            (adp/-delete-reaction lrs reaction-id)]
        (cond
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:reaction-id result}})
          (= :lrsql.reaction/reaction-not-found-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404
                  :body   {:error (format "The reaction \"%s\" does not exist!"
                                          (u/uuid->str reaction-id))}}))))}))
