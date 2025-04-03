(ns lrsql.admin.interceptors.xapi-credentials-override
  (:require
   [clojure.string :as string]
   [com.yetanalytics.lrs.pedestal.interceptor.auth :as ai]
   [lrsql.admin.interceptors.jwt :as jwt]
   [io.pedestal.interceptor.chain :as chain]
   [lrsql.input.auth :as auth-input]
   [lrsql.ops.query.auth :as auth-q]
   [lrsql.util :as util]
   [next.jdbc :as jdbc]))

(def replace-auth
  {:name ::replace-auth-interceptor
   :enter (fn replace-auth-interceptor [ctx]
            (let [lrs (ctx :com.yetanalytics/lrs)
                  conn (get-in lrs [:connection :conn-pool])
                  backend (:backend lrs)

                  account-id (get-in ctx [:request :session ::jwt/data :account-id])
                  cred-id (get-in ctx [:request :params :credentialID])

                  input (auth-input/query-credentials-input account-id)
                  acct-credentials (jdbc/with-transaction [tx conn]
                                     (auth-q/query-credentials backend tx input))

                  {api-key :api-key secret-key :secret-key} (->> acct-credentials
                                                                 (some #(when (= (str (:id %)) cred-id)
                                                                          %)))]
              (if (and api-key secret-key)
                (let [base64 (util/str->base64encoded-str (str api-key ":" secret-key))]
                  (-> ctx
                      (update-in [:request :params] dissoc :credentialID)
                      (assoc-in [:request :headers "authorization"]
                                (str "Basic " base64))
                      (dissoc :com.yetanalytics.pedestal-oidc/token :token)))
                (assoc (chain/terminate ctx)
                       :response
                       {:status 401
                        :body   {:error "Unauthorized Account!"}}))))})

(defn check-for-credential-id [payload]
  {:name ::check-for-credential-id-interceptor
   :enter (fn [{queue ::chain/queue :as ctx}]
            (if (get-in ctx [:request :params :credentialID])
              (let [result (assoc ctx ::chain/queue
                                  (-> (empty queue)
                                      (into payload)
                                      (into queue)))]
                result)
              ctx))})

(defn splice-before [target-fn payload coll]
  (reduce (fn [acc v]
            (cond-> acc
              (target-fn v) (conj payload)
              true          (conj v)))
          (empty coll) coll))

(defn add-credentials-override [routes validate-jwt oidc]
  (let [[decode validate _authorize ensure req] oidc
        [_ _ interceptors :as statements-route]
        (->> routes (some (fn [[path method :as r]]
                            (when (= ["statements" :get]
                                     [(last (string/split path #"/")) method])
                              r))))
        payload-interceptors (conj
                              ;;oidc interceptors, minus _authorize
                              (filterv identity [decode
                                                 validate
                                                 ensure
                                                 req])
                              validate-jwt 
                              replace-auth)
        
        new-interceptors (splice-before #(= (:name %) ::ai/lrs-authenticate)
                                        (check-for-credential-id payload-interceptors)
                                        interceptors)

        new-route (assoc statements-route 2 new-interceptors)]
    (-> routes
        (disj statements-route)
        (conj new-route))))
