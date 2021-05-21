(ns lrsql.hugsql.spec.activity
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json  :as json]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.hugsql.spec.util :refer [make-str-spec]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :lrsql.hugsql.spec.activity/activity-iri :activity/id)

(s/def :lrsql.hugsql.spec.activity/usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})

(s/def :lrsql.hugsql.spec.activity/payload
  (make-str-spec ::xs/activity
                    json/read-str
                    json/write-str))

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
