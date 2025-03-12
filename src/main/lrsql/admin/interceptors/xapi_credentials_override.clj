(ns lrsql.admin.interceptors.xapi-credentials-override
  (:require
   [com.yetanalytics.lrs.pedestal.interceptor.auth :as ai]
   [io.pedestal.interceptor.chain :as chain]
   [lrsql.admin.interceptors.jwt :as ji]
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

                  account-id (get-in ctx [:request :session :data :account-id])
                  cred-id (get-in ctx [:request :params :credentialID])

                  input (auth-input/query-credentials-input account-id)
                  acct-credentials (jdbc/with-transaction [tx conn]
                                     (auth-q/query-credentials backend tx input))

                  {api-key :api_key secret-key :secret_key} (->> acct-credentials
                                                                 (some #(when (= (:id %) cred-id)
                                                                          %)))]
              (when (and api-key secret-key)
                (let [base64 (util/str->base64encoded-str (str api-key ":" secret-key))]
                  (-> ctx (update-in [:request :params] dissoc :credentialID)
                      (assoc-in [:request :headers "authorization"]
                                (str "Basic " base64)))))))})

(defn check-for-credential-id [validate-jwt]
  {:name :check-for-credential-id-interceptor
   :enter (fn [{queue ::chain/queue :as ctx}]
            (if (get-in ctx [:request :params :credentialID])
              (assoc ctx ::chain/queue
                     (-> (empty queue)
                         (into [validate-jwt replace-auth])
                         (into queue)))
              ctx))})

(defn splice-before [target-fn payload coll]
  (reduce (fn [acc v]
            (cond-> acc
              (target-fn v) (conj payload)
              true          (conj v)))
          (empty coll) coll))

(defn add-no-val-credentials-override [routes validate-jwt]
  (let [insertion (check-for-credential-id validate-jwt)

        [_ _ interceptors :as statements-route]
        (->> routes (some (fn [[path method :as r]]
                           (when (= ["statements" :get]
                                    [(last (clojure.string/split path #"/")) method])
                             r))))

        new-interceptors (splice-before #(= (:name %) ::ai/lrs-authenticate)
                                        insertion
                                        interceptors)
        new-route (assoc statements-route 2 new-interceptors)]
    (-> routes
        (disj statements-route)
        (conj new-route))))
