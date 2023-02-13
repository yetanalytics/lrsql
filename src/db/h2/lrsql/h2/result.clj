(ns lrsql.h2.result
  "Process raw h2 results for parity with other backends"
  (:require [com.yetanalytics.squuid :as squuid]
            [lrsql.util :as u]))

(defn query-last-statement-stored-result
  "Extracts a timestamp suitable for last-statement-stored from an H2 query
  result. If result is nil, returns nil."
  [query-result]
  (when-let [{:keys [id]} query-result]
    {:lstored (-> id squuid/uuid->time u/time->str)}))

(defn query-platform-frequency-result
  "Given a result of all statement payloads, return the frequencies of each"
  [query-result]
  (map
   (fn [[platform scount]]
     {:platform platform
      :scount   scount})
   (frequencies
    (map #(get-in % [:payload "context" "platform"] "unknown")
         query-result))))
