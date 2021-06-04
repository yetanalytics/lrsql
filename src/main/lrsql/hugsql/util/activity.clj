(ns lrsql.hugsql.util.activity
  (:require [com.yetanalytics.lrs.xapi.activities :as as]))

(defn merge-activities
  "Given the Activity objects `old-activity` and `new-activity`, merge
   them such that their lang maps are merged."
  [old-activity new-activity]
  (as/merge-activity old-activity new-activity))
