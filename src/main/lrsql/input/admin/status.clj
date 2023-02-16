(ns lrsql.input.admin.status
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.squuid :as squuid]
            [lrsql.spec.admin.status :as stat]
            [lrsql.util :as u]))

(s/fdef query-timeline-input
  :args (s/cat :params stat/get-status-params-spec)
  :ret stat/query-timeline-input-spec)

(def unit-for
  "Mapping of timeline bucket time unit to suitable FOR sql substring arg."
  {"year"   4
   "month"  7
   "day"    10
   "hour"   13
   "minute" 16
   "second" 19})

(defn query-timeline-input
  "Transform parameters for timeline into values suitable for query."
  [{:keys [timeline-unit
           timeline-since
           timeline-until]
    :or   {timeline-unit "day"}}]
  (let [since' (or (some-> timeline-since u/str->time)
                   (u/offset-time (u/current-time) -14 :days))
        until' (or (some-> timeline-until u/str->time)
                   (u/current-time))]
    {:unit-for (get unit-for timeline-unit)
     :since-id (squuid/time->uuid since')
     :until-id (squuid/time->uuid until')}))
