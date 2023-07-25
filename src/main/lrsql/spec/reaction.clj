(ns lrsql.spec.reaction
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]))

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
  string?)

(s/def ::path
  (s/every
   (s/or :string string?
         :index nat-int?)
   :gen-max 4))

(s/def ::val ::xs/any-json)

(s/def :ref/condition ::condition-name)

(s/def ::ref
  (s/keys :req-un [:ref/condition
                   ::path]))

(s/def ::op
  #{"gt"
    "lt"
    "gte"
    "lte"
    "eq"
    "noteq"
    "like"
    "contains"})

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
  (s/map-of simple-keyword?
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

(s/def ::active boolean?)

(s/def ::primary-key uuid?)

(s/def ::reaction-id uuid?)

(s/def ::created c/instant-spec)

(s/def ::modified c/instant-spec)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-reaction-input-spec
  (s/keys :req-un [::ruleset
                   ::trigger-id
                   ::statement-identity]))

(s/def :lrsql.spec.reaction.serialized/ruleset bytes?)

(def insert-reaction-input-spec
  (s/keys :req-un [::primary-key
                   :lrsql.spec.reaction.serialized/ruleset
                   ::active
                   ::created
                   ::modified]))

(def update-reaction-input-spec
  (s/keys :req-un [::reaction-id
                   ::modified]
          :opt-un [:lrsql.spec.reaction.serialized/ruleset
                   ::active]))

(def delete-reaction-input-spec
  (s/keys :req-un [::reaction-id]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-reaction-ret-spec
  (s/every (s/map-of ::condition-name ::xs/statement)))

(s/def ::id ::reaction-id)

(def query-active-reactions-ret-spec
  (s/every (s/keys :req-un [::id
                            ::ruleset])))

(s/def :lrsql.spec.reaction.insert/result uuid?)

(def insert-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.insert/result]))

(s/def :lrsql.spec.reaction.update/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/reaction-not-found-error})))

(def update-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.update/result]))

(s/def :lrsql.spec.reaction.delete/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/reaction-not-found-error})))

(def delete-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.delete/result]))
