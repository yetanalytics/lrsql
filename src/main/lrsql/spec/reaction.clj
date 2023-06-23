(ns lrsql.spec.reaction
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]))

(s/def ::condition-name
  simple-keyword?)

(s/def ::path
  (s/every
   (s/or :key simple-keyword?
         :index nat-int?)
   :gen-max 4))

(s/def ::val ::xs/any-json)

(s/def :ref/condition ::condition-name)

(s/def ::ref
  (s/keys :req-un [:ref/condition
                   ::path]))

(s/def ::operand
  (s/or :val (s/keys :req-un [::val])
        :ref (s/keys :req-un [::ref])))

(s/def ::gt ::operand)
(s/def ::lt ::operand)
(s/def ::gte ::operand)
(s/def ::lte ::operand)
(s/def ::noteq ::operand)
(s/def ::eq ::operand)
(s/def ::contains ::operand)
(s/def ::contains-str ::operand)

(s/def ::clause
  (s/merge
   (s/keys :req-un [::path])
   (s/or :gt (s/keys :req-un [::gt])
         :lt (s/keys :req-un [::lt])
         :gte (s/keys :req-un [::gte])
         :lte (s/keys :req-un [::lte])
         :noteq (s/keys :req-un [::noteq])
         :eq (s/keys :req-un [::eq])
         :contains (s/keys :req-un [::contains])
         :contains-str (s/keys :req-un [::contains-str]))))

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

(s/def ::input
  (s/keys :req-un [::conditions]))

(comment

  (require '[clojure.spec.gen.alpha :as sgen])

  (sgen/generate (s/gen ::input))


  (s/valid? ::input
            {:conditions
             {:a
              {:and
               [{:path [:object :id],
                 :eq   {:val "https://example.com/activities/a"}}
                {:path [:verb :id],
                 :eq   {:val "https://example.com/verbs/completed"}}
                {:path [:result :success], :eq {:val true}}]},
              :b
              {:and
               [{:path [:object :id],
                 :eq   {:val "https://example.com/activities/b"}}
                {:path [:verb :id],
                 :eq   {:val "https://example.com/verbs/completed"}}
                {:path [:result :success], :eq {:val true}}
                {:path [:timestamp],
                 :gt   {:ref {:condition :a, :path [:timestamp]}}}]}}})

  )
