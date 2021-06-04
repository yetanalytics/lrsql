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
                      "account" {"homePage" "http://localhost:8080"
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

(defn format-statement
  "Given `statement`, format it according to the value of `format`:
   - :exact      No change to the Statement
   - :ids        Return only the IDs in each Statement object
   - :canonical  Return a \"canonical\" version of lang maps based on `ltags`."
  [statement format ltags]
  (case format
    :exact
    statement
    :ids
    (ss/format-statement-ids statement)
    :canonical
    (ss/format-canonical statement ltags)
    ;; else
    (throw (ex-info "Unknown format type"
                    {:kind   ::unknown-format-type
                     :format format}))))

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
  "Forms the `more` URL value from `query-params` and the Statement ID
   `next-cursor` which points to the first Statement of the next page."
  [query-params next-cursor]
  (str (xapi-path-prefix)
       "/xapi/statements?"
       (form-encode (assoc query-params :from next-cursor))))

(defn ensure-default-max-limit
  "Apply default/max limit to params"
  [?limit]
  (let [;; TODO: env defaults out of code.. Aero?
        ;; TODO: reevaluate defaults
        limit-max     (:stmt-get-max env 100)
        limit-default (:stmt-get-default env 100)]
    (cond
      ;; ensure limit is =< max
      (pos-int? ?limit)
      (min ?limit limit-max)
      ;; if zero, spec says use max
      (and ?limit (zero? ?limit))
      limit-max
      ;; otherwise default
      :else
      limit-default)))
