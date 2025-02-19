(ns lrsql.auth.interceptor
  (:require
   [io.pedestal.interceptor :as i]
   [lrsql.input.auth :as auth-input]
   [lrsql.ops.query.auth :as auth-q]
   [lrsql.util :as util]
   [next.jdbc :as jdbc]))

(def holder (atom nil))
(def h2 (atom nil))
(def h3 (atom nil))

(defn auth-by-cred-id-interceptor [lrs]
  {:name ::auth-by-cred-id-interceptor
   :enter (fn auth-by-cred-id-interceptor [ctx]
            (reset! h3 ctx)
            (println "triggered")
            (if-let [cred-id (get-in ctx [:request :params :credentialID])]
              (let [conn (-> lrs :connection :conn-pool)
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
