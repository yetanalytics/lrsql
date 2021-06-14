(ns lrsql.ops.query.auth
  (:require [lrsql.functions :as f]
            [lrsql.util.auth :as ua]))

(defn query-api-keys
  [tx input]
  (if-some [scopes (some->> (f/query-credential-scopes tx input)
                            (map :scope)
                            not-empty)]
    ;; Credentials found - return result map
    (let [{:keys [api-key secret-api-key]}
          input
          scope-set (if (every? nil? scopes)
                      ;; Credentials not associated with any scope.
                      ;; The LRS MUST assume a requested scope of
                      ;; "statements/write" and "statements/read/mine"
                      ;; if no scope is specified.
                      #{:scopes/statements.write
                        :scopes/statements.read.mine}
                      ;; Return scope set
                      (->> scopes
                           (filter some?)
                           (map ua/scope-str->kw)
                           (into #{})))]
      {:result {:scopes scope-set
                :prefix ""
                :auth   {:username api-key
                         :password secret-api-key}}})
    ;; Credentials not found - uh oh!
    {:result :com.yetanalytics.lrs.auth/forbidden}))
