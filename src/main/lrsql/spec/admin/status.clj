(ns lrsql.spec.admin.status
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as common]
            [xapi-schema.spec :as xs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-status-backend?
  [bk]
  (satisfies? bp/AdminStatusBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::count nat-int?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::status-query-name
  #{"statement-count"
    "actor-count"
    "platform-frequency"
    "last-statement-stored"
    "timeline"})

(s/def :lrsql.spec.admin.status.params/include
  (s/nonconforming
   (s/or :single   ::status-query-name
         :multiple (s/every ::status-query-name))))
(s/def :lrsql.spec.admin.status.params/timeline-unit
  #{"year"
    "month"
    "day"
    "hour"
    "minute"
    "second"})
(s/def :lrsql.spec.admin.status.params/timeline-since
  ::xs/timestamp)
(s/def :lrsql.spec.admin.status.params/timeline-until
  ::xs/timestamp)


(def get-status-params-spec
  (s/keys :opt-un [:lrsql.spec.admin.status.params/include
                   :lrsql.spec.admin.status.params/timeline-unit
                   :lrsql.spec.admin.status.params/timeline-since
                   :lrsql.spec.admin.status.params/timeline-until]))

;; Conversion of the above unit to be the "FOR" argument in a substring call
(s/def :lrsql.spec.admin.status.query.timeline/unit-for
  #{4
    7
    10
    13
    16
    19})
(s/def :lrsql.spec.admin.status.query.timeline/since-id
  ::common/primary-key)
(s/def :lrsql.spec.admin.status.query.timeline/until-id
  ::common/primary-key)

(def query-timeline-input-spec
  (s/keys :req-un [:lrsql.spec.admin.status.query.timeline/unit-for
                   :lrsql.spec.admin.status.query.timeline/since-id
                   :lrsql.spec.admin.status.query.timeline/until-id]))

(s/def :lrsql.spec.admin.status.query/include
  (s/every ::status-query-name
           :kind set?
           :into #{}))
(s/def :lrsql.spec.admin.status.query/timeline
  query-timeline-input-spec)

(def query-status-input-spec
  (s/keys :req-un [:lrsql.spec.admin.status.query/include]
          :opt-un [:lrsql.spec.admin.status.query/timeline]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::statement-count ::count)
(s/def ::actor-count ::count)
(s/def ::last-statement-stored (s/nilable ::xs/timestamp))
(s/def ::platform-frequency
  (s/map-of string?
            ::count))

(s/def ::timeline
  (s/every (s/keys :req-un [:statement/stored
                            ::count])))

(def query-status-ret-spec
  (s/keys :req-un [::statement-count
                   ::actor-count
                   ::last-statement-stored
                   ::platform-frequency]))
