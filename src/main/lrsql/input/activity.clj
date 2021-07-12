(ns lrsql.input.activity
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.spec.common :as c]
            [lrsql.spec.activity :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Activity Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-activity-input
  :args (s/cat :activity ::xs/activity)
  :ret ::as/activity-input)

(defn insert-activity-input
  "Given `activity`, construct the input for `functions/insert-activity!`."
  [activity]
  {:table        :activity
   :primary-key  (u/generate-squuid)
   :activity-iri (get activity "id")
   :payload      activity})

(s/fdef insert-statement-to-activity-input
  :args (s/cat :statement-id ::c/statement-id
               :activity-usage ::as/usage
               :activity-input ::as/activity-input)
  :ret ::as/stmt-activity-input)

(defn insert-statement-to-activity-input
  "Given `statement-id`, `activity-usage` and the return value of
   `activity-insert-input`, return the input for
   `functions/insert-statement-to-activity!`."
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (u/generate-squuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Activity Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-activity-input
  :args (s/cat :params ::lrsp/get-activity-params)
  :ret as/query-activity-spec)

(defn query-activity-input
  "Construct an input for `query-activity!`"
  [{activity-id :activityId}]
  {:activity-iri activity-id})
