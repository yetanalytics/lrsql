(ns lrsql.hugsql.command.activity
  (:require [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]))

(defn query-activity
  "Query an Activity from the DB. Returns a map between `:activity` and the
   activity found, which is nil if not found."
  [tx input]
  (let [{:keys [payload]} (f/query-activity tx input)]
    {:activity (some->> payload u/parse-json)}))
