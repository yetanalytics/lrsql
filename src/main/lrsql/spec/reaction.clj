(ns lrsql.spec.reaction
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.backend.protocol :as bp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reaction-backend?
  [bk]
  (satisfies? bp/ReactionBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::condition-name
  simple-keyword?)

(s/def ::path
  (s/every
   (s/or :key simple-keyword?
         :string string?
         :index nat-int?)
   :gen-max 4))

(s/def ::val ::xs/any-json)

(s/def :ref/condition ::condition-name)

(s/def ::ref
  (s/keys :req-un [:ref/condition
                   ::path]))

(s/def ::op
  #{:gt
    :lt
    :gte
    :lte
    :eq
    :noteq
    :like
    :contains})

(s/def ::clause
  (s/or :clause-val
        (s/keys :req-un [::path
                         ::op
                         ::val])
        :clause-ref
        (s/keys :req-un [::path
                         ::op
                         ::ref])))

(declare condition-spec)

(s/def ::and (s/every condition-spec
                      :min-count 1
                      :gen-max 3))
(s/def ::or (s/every condition-spec
                     :min-count 1
                     :gen-max 3))
(s/def ::not condition-spec)

(s/def ::boolean
  (s/or :and (s/keys :req-un [::and])
        :or (s/keys :req-un [::or])
        :not (s/keys :req-un [::not])))

(def condition-spec
  (s/or
   :clause ::clause
   :boolean ::boolean))

(s/def ::condition
  condition-spec)

(s/def ::conditions
  (s/map-of ::condition-name
            ::condition
            :min-count 1
            :gen-max 3))

(s/def ::identity-paths
  (s/every ::path))

(s/def ::ruleset
  (s/keys :req-un [::identity-paths
                   ::conditions]))

(s/def ::sqlvec
  (s/cat :ddl string?
         :params (s/* any?)))

(s/def ::statement-identity
  (s/map-of ::path (s/or :string string? :number number? :boolean boolean?)))

(s/def ::trigger-id :statement/id)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-reaction-input-spec
  (s/keys :req-un [::ruleset
                   ::trigger-id
                   ::statement-identity]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
