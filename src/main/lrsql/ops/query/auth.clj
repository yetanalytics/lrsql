(ns lrsql.ops.query.auth
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.auth :as as]
            [lrsql.util.auth :as au]))

(s/fdef query-credential-scopes*
  :args (s/cat :inf as/credential-interface?
               :tx transaction?
               :input as/query-cred-scopes-input-spec)
  :ret (s/nilable (s/coll-of ::as/scope
                             :min-count 1
                             :gen-max 5)))

(defn query-credential-scopes*
  "Return a vec of scopes associated with an API key and secret if it
   exists in the credential table; return nil if not."
  [inf tx input]
  (when (ip/-query-credential-exists inf tx input)
    (some->> (ip/-query-credential-scopes inf tx input)
             (map :scope)
             (filter some?)
             vec)))

(s/fdef query-credential-scopes
  :args (s/cat :inf as/credential-interface?
               :tx transaction?
               :input as/query-cred-scopes-input-spec)
  :ret ::lrsp/authenticate-ret)

(defn query-credential-scopes
  "Like `query-credential-scopes*` except that its return value conforms
   to the expectations of the lrs lib. In particular, returns a result
   map containins the scope and auth key map on success. If the credentials
   are not found, return a keyword to indicate that the webserver will
   return 401 Forbidden."
  [inf tx input]
  (if-some [scopes (query-credential-scopes* inf tx input)]
    ;; Credentials found - return result map
    (let [{:keys [api-key secret-key]}
          input
          scope-set
          (if (empty? scopes)
            ;; Credentials not associated with any scope.
            ;; The LRS MUST assume a requested scope of
            ;; "statements/write" and "statements/read/mine"
            ;; if no scope is specified.
            #{:scopes/statements.write
              :scopes/statements.read.mine}
            ;; Return scope set
            (->> scopes
                 (map au/scope-str->kw)
                 (into #{})))]
      {:result {:scopes scope-set
                :prefix ""
                :auth   {:basic {:username api-key
                                 :password secret-key}}}})
    ;; Credentials not found - uh oh!
    {:result :com.yetanalytics.lrs.auth/forbidden}))

(s/fdef query-credentials
  :args (s/cat :inf as/credential-interface?
               :tx transaction?
               :input as/query-creds-input-spec)
  :ret (s/coll-of as/scoped-key-pair-spec :gen-max 5))

(defn query-credentials
  "Given an input containing `:account-id`, return all creds (and their
   associated scopes) that are associated with that account."
  [inf tx input]
  (let [creds  (->> input
                    (ip/-query-credentials inf tx)
                    (map (fn [{ak :api_key sk :secret_key}]
                           {:api-key ak :secret-key sk})))
        scopes (doall (map (fn [cred]
                             (->> cred
                                  (ip/-query-credential-scopes inf tx)
                                  (map :scope)))
                           creds))]
    (mapv (fn [cred cred-scopes]
            (assoc cred :scopes (set cred-scopes)))
          creds
          scopes)))
