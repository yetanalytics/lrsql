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
    (map #(get-in % [:payload "context" "platform"] "none")
         query-result))))

(defn query-timeline-result
  "Given an input and range of statement IDs, return a bucketed timeline of
  counts."
  [{:keys [unit-for]} query-result]
  (->> query-result
       (map (comp
             u/time->str
             squuid/uuid->time
             :id))
       (reduce
        (fn [m stamp]
          (update m
                  (subs stamp 0 unit-for)
                  (fnil inc 0)))
        {})
       (map (fn [[stored cnt]]
              {:stored stored
               :scount cnt}))))
