(ns lrsql.auth.interceptor
  (:require
   [lrsql.input.auth :as auth-input]
   [lrsql.ops.query.auth :as auth-q]
   [lrsql.util :as util]))

(def holder (atom nil))
(def h2 (atom nil))
(def h3 (atom nil))

(def auth-by-cred-id-interceptor
  {:name :auth-by-cred-id-interceptor
   :enter (fn auth-by-cred-id-interceptor [ctx]
            (reset! h3 ctx)
            (println "triggered")
            (let [cred-id (get-in ctx [:request :params :credentialID])]
              (if cred-id
                (do
                  (println "triggered statements")
                  (-> ctx
                      (update-in [:request :params] dissoc :credentialID)
                                        ;next spoof basic auth
                      (assoc-in [:request :com.yetanalytics.url-credential-ID] cred-id)))
                ctx)))})


(defn insert-id-auth-interceptor [routes]
  (reset! holder routes)
  (let [statements? (fn [[path method]]
                      (and 
                       (= "statements"
                          (last (clojure.string/split path #"/")))
                       (= method :get)))
        map-fn (fn [route]
                 (if (statements? route)
                   (update-in route [2] (partial into [auth-by-cred-id-interceptor]))
                   route))]
    (reset! h2 (->> routes
                    (map map-fn)
                    (set)))))




#_(let [routes @holder
      statements?  (fn [[path method]]
                     (and 
                      (= "statements"
                         (last (clojure.string/split path #"/")))
                      (= method :get)))
      map-fn (fn [route]
               (if (vector? route) (println "vector") (println "not vector" ))
               (if (statements? route)
                 (do 
                   (println route)
                   (update-in route [2] (partial into [auth-by-cred-id-interceptor]))) 
                 route))]
  

  (->> routes
       (map map-fn)
       (set)))
