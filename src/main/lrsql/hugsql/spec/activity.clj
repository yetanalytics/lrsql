(ns lrsql.hugsql.spec.activity
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::activity-iri :activity/id)
(s/def ::?activity-iri (s/nilable ::activity-iri))

(s/def ::usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})

;; JSON string version: (make-str-spec ::xs/activity u/parse-json u/write-json)
(s/def ::payload ::xs/activity)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-activity-params
  ::lrsp/get-activity-params)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def activity-query-spec
  (s/keys :req-un [::activity-iri]))
