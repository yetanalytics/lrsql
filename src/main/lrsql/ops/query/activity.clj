(ns lrsql.ops.query.activity
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.activity :as as]))

(s/fdef query-activity
  :args (s/cat :interface c/query-interface?
               :tx c/transaction?
               :input as/query-activity-spec)
  :ret ::lrsp/get-activity-ret)

(defn query-activity
  "Query an Activity from the DB. Returns a map between `:activity` and the
   activity found, which is `nil` if not found."
  [interface tx input]
  (let [{activity :payload} (ip/-query-activity interface tx input)]
    {:activity activity}))
