(ns lrsql.hugsql.spec.activity
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json  :as json]
            [xapi-schema.spec   :as xs]
            [lrsql.hugsql.spec.util :refer [make-str-spec]]))

(s/def :lrsql.hugsql.spec.activity/activity-iri :activity/id)

(s/def :lrsql.hugsql.spec.activity/usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})

(s/def :lrsql.hugsql.spec.activity/payload
  (make-str-spec ::xs/activity
                    json/read-str
                    json/write-str))
