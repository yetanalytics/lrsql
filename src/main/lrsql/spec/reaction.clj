(ns lrsql.spec.reaction
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.backend.protocol :as bp]))

(defn reaction-backend?
  [bk]
  (satisfies? bp/ReactionQueryBackend bk))

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
    :contains
    :contains-str})

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

(s/def ::trigger-id
  :statement/id)

(s/def ::input
  (s/keys :req-un [::conditions
                   ::trigger-id]))

(s/def ::sqlvec
  (s/cat :ddl string?
         :params (s/* any?)))

(comment

  (require '[clojure.spec.gen.alpha :as sgen])

  (sgen/generate (s/gen ::input))

  (s/valid? ::input
            {:conditions
             {:a
              {:and
               [{:path [:object :id]
                 :op   :eq
                 :val  "https://example.com/activities/a"}
                {:path [:verb :id]
                 :op   :eq
                 :val  "https://example.com/verbs/completed"}
                {:path [:result :success]
                 :op   :eq
                 :val  true}]}
              :b
              {:and
               [{:path [:object :id]
                 :op   :eq
                 :val  "https://example.com/activities/b"}
                {:path [:verb :id]
                 :op   :eq
                 :val  "https://example.com/verbs/completed"}
                {:path [:result :success]
                 :op   :eq
                 :val  true}
                {:path [:timestamp]
                 :op   :gt
                 :ref  {:condition :a, :path [:timestamp]}}]}}})

  )
