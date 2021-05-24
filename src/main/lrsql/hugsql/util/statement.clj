(ns lrsql.hugsql.util.statement
  (:require [config.core :refer [env]]
            [ring.util.codec :refer [form-encode]]
            [lrsql.hugsql.util :as u]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;; If a Statement lacks a version, the version MUST be set to 1.0.0
;; TODO: Change for version 2.0.0
(def xapi-version "1.0.0")

;; TODO: more specific authority
(def lrsql-authority {"name" "LRSQL"
                      "objectType" "Agent"
                      "account" {"homepage" "http://localhost:8080"
                                 "name"     "LRSQL"}})

(defn prepare-statement
  "Prepare `statement` for LRS storage by coll-ifying context activities
   and setting missing id, timestamp, authority, version, and stored
   properties."
  [statement]
  (let [{:strs [id timestamp authority version]} statement]
    ;; first coll-ify context activities
    (cond-> (ss/fix-statement-context-activities statement)
      true ; stored is always set by the LRS
      (assoc "stored" (u/time->str (u/current-time)))
      (not id)
      (assoc "id" (u/uuid->str (u/generate-uuid)))
      (not timestamp)
      (assoc "timestamp" (u/time->str (u/current-time)))
      (not authority)
      (assoc "authority" lrsql-authority)
      (not version)
      (assoc "version" xapi-version))))

;; TODO: Get more permanent solution for host and port defaults
(defn- xapi-path-prefix
  []
  (let [{host :db-host
         port :db-port
         :or {host "localhost"
              port 8080}}
        env]
    (str "http://" host ":" port)))

(defn make-more-url
  "If `stmt-query-result` contains a non-empty `more` string signifying the
   pagination cursor, update it to be a URL to query the next page."
  [params stmt-query-result]
  (if-some [stmt-id (not-empty (get-in stmt-query-result
                                       [:statement-result :more]))]
    (assoc-in stmt-query-result
              [:statement-result :more]
              (str (xapi-path-prefix)
                   "/xapi/statements?"
                   (form-encode (assoc params :from stmt-id))))
    stmt-query-result))
