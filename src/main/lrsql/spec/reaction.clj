(ns lrsql.spec.reaction
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.backend.protocol :as bp]
            [lrsql.reaction.protocol :as rp]
            [lrsql.spec.common :as c]
            [com.yetanalytics.lrs-reactions.spec :as rs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reaction-backend?
  [bk]
  (satisfies? bp/ReactionBackend bk))

(defn reactor?
  [x]
  (satisfies? rp/StatementReactor x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; To reduce footprint some specs just point to the shared lib so we don't need
;; two dependencies everywhere.

(s/def ::condition-name
  ::rs/condition-name)

(s/def ::condition
  ::rs/condition)

(s/def ::path
  ::rs/path)

(s/def ::datatype keyword?)

(s/def ::identityPaths
  ::rs/identityPaths)

(s/def ::ruleset
  ::rs/ruleset)

(s/def ::sqlvec
  (s/cat :ddl string?
         :params (s/* any?)))

(s/def ::statement-identity
  (s/map-of ::path (s/or :string string? :number number? :boolean boolean?)))

(s/def ::trigger-id uuid?)

(s/def ::trigger-stored inst?)

(s/def ::active boolean?)

(s/def ::primary-key uuid?)

(s/def ::reaction-id uuid?)

(s/def ::title string?)

(s/def ::created c/instant-spec)

(s/def ::modified c/instant-spec)

(s/def ::error
  ::rs/error)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-reaction-input-spec
  (s/keys :req-un [::ruleset
                   ::trigger-id
                   ::trigger-stored
                   ::statement-identity]))

(def query-statement-reactions-input-spec
  (s/keys :req-un [::trigger-id]))

(s/def :lrsql.spec.reaction.query-reaction-history/statement-id
  uuid?)

(def query-reaction-history-input-spec
  (s/keys
   :req-un [:lrsql.spec.reaction.query-reaction-history/statement-id]))

(def insert-reaction-input-spec
  (s/keys :req-un [::primary-key
                   ::title
                   ::ruleset
                   ::active
                   ::created
                   ::modified]))

(def update-reaction-input-spec
  (s/keys :req-un [::reaction-id
                   ::modified]
          :opt-un [::ruleset
                   ::active
                   ::title]))

(def delete-reaction-input-spec
  (s/keys :req-un [::reaction-id
                   ::modified]))

(def error-reaction-input-spec
  (s/keys :req-un [::reaction-id
                   ::modified
                   ::error]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-reaction-ret-spec
  (s/every (s/map-of simple-keyword? ::xs/statement)))

(s/def ::id ::reaction-id)

(def query-active-reactions-ret-spec
  (s/every (s/keys :req-un [::id
                            ::ruleset])))

(s/def :lrsql.spec.reaction.query-all-reactions/error
  (s/nilable ::error))

(def query-all-reactions-ret-spec
  (s/every (s/keys :req-un [::id
                            ::title
                            ::ruleset
                            ::active
                            ::created
                            ::modified
                            :lrsql.spec.reaction.query-all-reactions/error])))

(def query-statement-reactions-ret-element-spec
  (s/merge
   (s/keys :req-un [::trigger-id
                    ::reaction-id])
   (s/or :success (s/keys :req-un [::xs/statement
                                   :statement/authority])
         :failure (s/keys :req-un [::error]))))

(s/def :lrsql.spec.reaction.query-statement-reactions/result
  (s/every query-statement-reactions-ret-element-spec))

(def query-statement-reactions-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.query-statement-reactions/result]))

(s/def :lrsql.spec.reaction.query-reaction-history/result
  (s/every uuid? :kind set? :into #{}))

(def query-reaction-history-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.query-reaction-history/result]))

(s/def :lrsql.spec.reaction.insert/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/title-conflict-error})))

(def insert-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.insert/result]))

(s/def :lrsql.spec.reaction.update/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/reaction-not-found-error
                    :lrsql.reaction/title-conflict-error})))

(def update-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.update/result]))

(s/def :lrsql.spec.reaction.delete/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/reaction-not-found-error})))

(def delete-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.delete/result]))

(s/def :lrsql.spec.reaction.error-reaction/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.reaction/reaction-not-found-error})))

(def error-reaction-ret-spec
  (s/keys :req-un [:lrsql.spec.reaction.error-reaction/result]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-reaction-params-spec
  (s/keys :req-un [::title
                   ::ruleset
                   ::active]))

(def update-reaction-params-spec
  (s/keys :req-un [::reaction-id
                   (or ::ruleset
                       ::active
                       ::title)]))

(def delete-reaction-params-spec
  (s/keys :req-un [::reaction-id]))
