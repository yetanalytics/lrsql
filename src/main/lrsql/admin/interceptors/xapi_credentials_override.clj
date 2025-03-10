(ns lrsql.admin.interceptors.xapi-credentials-override
  (:require
   [lrsql.input.auth :as auth-input]
   [lrsql.ops.query.auth :as auth-q]
   [lrsql.util :as util]
   [next.jdbc :as jdbc]))

(defn auth-by-cred-id-interceptor [lrs]
  {:name ::auth-by-cred-id-interceptor
   :enter (fn auth-by-cred-id-interceptor [ctx]
            (let [modded-ctx
                  (when-let [cred-id (get-in ctx [:request :params :credentialID])]
                    (let [conn (-> lrs :connection :conn-pool)
                          backend (:backend lrs)
                          input (auth-input/query-credential-by-id-input cred-id)
                          {api-key :api_key secret-key :secret_key}
                          (jdbc/with-transaction [tx conn]
                            (auth-q/query-credential-by-id backend tx input))]
                      (when (and api-key secret-key)
                        (let [base64 (util/str->base64encoded-str (str api-key ":" secret-key))]
                          (-> ctx (update-in [:request :params] dissoc :credentialID)
                              (assoc-in [:request :headers "authorization"]
                                        (str "Basic " base64)))))))]
              (or modded-ctx ctx)))})
