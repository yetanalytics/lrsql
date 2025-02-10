(ns lrsql.ops.query.auth
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.auth :as as]
            [lrsql.util.auth :as au]))

(s/fdef query-credential-scopes*
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :input as/query-cred-scopes*-input-spec)
  :ret (s/nilable (s/keys :req-un [::as/ids ::as/scopes])))

(defn- conform-credential-ids
  [{cred-id :cred_id account-id :account_id}]
  {:cred-id    cred-id
   :account-id account-id})

(defn query-credential-scopes*
  "Return a map of `:ids` and `:scopes` associated with an API key and secret
   if it exists in the credential table; return nil if not."
  [bk tx input]
  (when-some [ids (bp/-query-credential-ids bk tx input)]
    (let [scopes (some->> (bp/-query-credential-scopes bk tx input)
                          (map :scope)
                          (filter some?)
                          vec)]
      {:ids    (conform-credential-ids ids)
       :scopes scopes})))

(s/fdef query-credential-scopes
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :input as/query-cred-scopes-input-spec)
  :ret ::lrsp/authenticate-ret)

(defn query-credential-scopes
  "Like `query-credential-scopes*` except that its return value conforms
   to the expectations of the lrs lib. In particular, returns a result
   map that contains the `:scope` `:auth`, and `:agent` properties. The
   `:agent` property can then be set as the Statement authority.
   
   If the credentials are not found, return `::lrs-auth/unauthorized` to
   indicate that the webserver will return 401 Unauthorized."
  [bk tx input]
  (if-some [{:keys [ids scopes]} (query-credential-scopes* bk tx input)]
    ;; Credentials found - return result map
    (let [{:keys [api-key
                  secret-key
                  authority-fn
                  authority-url]} input
          ;; NOTE: According to the xAPI spec, the LRS MUST assume scopes
          ;; "statements/write" and "statements/read/mine" if no scope is
          ;; specified, if OAuth 1.0 is used. Since we are not using OAuth 1.0
          ;; and since the above behavior is confusing, we simply return empty
          ;; scope sets as-is.
          scope-set (->> scopes
                         (map au/scope-str->kw)
                         (into #{}))]
      {:result {:scopes scope-set
                :prefix ""
                :auth   {:basic {:username api-key
                                 :password secret-key}}
                :agent  (authority-fn
                         (assoc ids :authority-url authority-url))}})
    ;; Credentials not found - uh oh!
    {:result :com.yetanalytics.lrs.auth/unauthorized}))

(s/fdef query-credentials
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :input as/query-creds-input-spec)
  :ret (s/coll-of as/scoped-key-pair-spec :gen-max 5))

(defn query-credentials
  "Given an input containing `:account-id`, return all creds (and their
   associated scopes) that are associated with that account."
  [bk tx input]
  (let [creds  (->> input
                    (bp/-query-credentials bk tx)
                    (map (fn [{ak    :api_key
                               sk    :secret_key
                               label :label
                               seed? :is_seed}]
                           (cond-> {:api-key    ak
                                    :secret-key sk
                                    :label      label}
                             (true? seed?)
                             (assoc :seed? seed?)))))
        scopes (doall (map (fn [cred]
                             (->> cred
                                  (bp/-query-credential-scopes bk tx)
                                  (map :scope)))
                           creds))]
    (mapv (fn [cred cred-scopes]
            (assoc cred :scopes (set cred-scopes)))
          creds
          scopes)))
