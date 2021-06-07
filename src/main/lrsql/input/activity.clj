(ns lrsql.input.activity
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.activity :as hs]))

(s/fdef activity-query-input
  :args (s/cat :params hs/get-activity-params)
  :ret hs/activity-query-spec)

(defn activity-query-input
  "Construct an input for `query-activity!`"
  [{activity-id :activityId}]
  {:activity-iri activity-id})
