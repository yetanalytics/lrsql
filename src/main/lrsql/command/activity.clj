(ns lrsql.command.activity
  (:require [lrsql.functions :as f]
            [lrsql.util :as u]))

(defn query-activity
  "Query an Activity from the DB. Returns a map between `:activity` and the
   activity found, which is nil if not found."
  [tx input]
  (let [activity (some-> (f/query-activity tx input)
                         :payload
                         u/parse-json)]
    {:activity activity}))
