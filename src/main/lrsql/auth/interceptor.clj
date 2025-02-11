(ns lrsql.auth.interceptor
  (:require
   [lrsql.input.auth :as auth-input]
   [lrsql.ops.query.auth :as auth-q]
   [lrsql.util :as util]
   [next.jdbc :as jdbc]))

(def holder (atom nil))
(def h2 (atom nil))
(def h3 (atom nil))

(def auth-by-cred-id-interceptor
  {:name :auth-by-cred-id-interceptor
   :enter (fn auth-by-cred-id-interceptor [ctx]
            (reset! h3 ctx)
            (println "triggered")
            (if-let [cred-id (get-in ctx [:request :params :credentialID])]
              (let [lrs (:com.yetanalytics/lrs ctx)
                    conn (-> lrs :connection :conn-pool)
                    backend (:backend lrs)
                    input (auth-input/query-credential-by-id-input cred-id)

                    {api-key :api_key secret-key :secret_key :as result}
                    (jdbc/with-transaction [tx conn]
                      (auth-q/query-credential-by-id backend tx input))

                    _ (println "result:" result)
                    base64 (util/str->base64encoded-str (str api-key ":" secret-key))]
                
                (println "triggered statements")
                (-> ctx
                    (update-in [:request :params] dissoc :credentialID)
                    (assoc-in [:request :headers "authorization"]
                              (str "Basic " base64))))
              ctx))})

(defn insert-after-lrs-interceptor [interceptors interceptor]
  (reduce (fn [past {:keys [name] :as next}]
            (cond-> past
              true
              (conj next)

              (= name :com.yetanalytics.lrs.pedestal.interceptor/lrs)
              (conj interceptor)))
          [] interceptors))

(defn insert-id-auth-interceptor [routes]
  (reset! holder routes)
  (let [statements? (fn [[path method]]
                      (and 
                       (= "statements"
                          (last (clojure.string/split path #"/")))
                       (= method :get)))
        map-fn (fn [route]
                 (if (statements? route)
                   (update-in route [2] insert-after-lrs-interceptor auth-by-cred-id-interceptor)
                   route))]
    (reset! h2 (->> routes
                    (map map-fn)
                    (set)))))



