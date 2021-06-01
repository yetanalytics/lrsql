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
  (let [{:strs [id timestamp authority version]} statement
        {squuid      :squuid
         squuid-ts   :timestamp
         squuid-base :base-uuid} (u/generate-squuid*)
        squuid-ts-str (u/time->str squuid-ts)
        statement'    (-> statement
                          ss/fix-statement-context-activities
                          (assoc "stored" squuid-ts-str)
                          (vary-meta assoc :primary-key squuid))]
    (cond-> statement'
      (not id)
      (assoc "id" (u/uuid->str squuid-base))
      (not timestamp)
      (assoc "timestamp" squuid-ts-str)
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

(defn ensure-default-max-limit
  "Apply default/max limit to params"
  [{:keys [limit]
    :as params}]
  (let [;; TODO: env defaults out of code.. Aero?
        ;; TODO: reevaluate defaults
        limit-max     (:stmt-get-max env 100)
        limit-default (:stmt-get-default env 100)]
    (assoc params
           :limit
           (cond
             ;; ensure limit is =< max
             (pos-int? limit)
             (min limit
                  limit-max)
             ;; if zero, spec says use max
             (and limit (zero? limit))
             limit-max
             ;; otherwise default
             :else
             limit-default))))
