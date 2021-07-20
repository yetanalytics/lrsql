(ns lrsql.spec.activity
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn activity-interface?
  [inf]
  (satisfies? ip/ActivityInterface inf))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::activity-iri :activity/id)

(s/def ::usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})

;; JSON string version: (make-str-spec ::xs/activity u/parse-json u/write-json)
(s/def ::payload ::xs/activity)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - activity_iri: STRING NOT NULL UNIQUE KEY
;; - payload:      JSON NOT NULL

(s/def ::activity-input
  (s/keys :req-un [::c/primary-key
                   ::activity-iri
                   ::payload]))

(s/def ::activity-inputs
  (s/coll-of ::activity-input :gen-max 5))

;; Statement-to-Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Object', 'Category', 'Grouping', 'Parent', 'Other',
;;                       'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther')
;;                 NOT NULL
;; - activity_iri: STRING NOT NULL FOREIGN KEY

(s/def ::stmt-activity-input
  (s/keys :req-un [::c/primary-key
                   ::c/statement-id
                   ::usage
                   ::activity-iri]))

(s/def ::stmt-activity-inputs
  (s/coll-of ::stmt-activity-input :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-activity-spec
  (s/keys :req-un [::activity-iri]))
