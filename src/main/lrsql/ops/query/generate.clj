(ns lrsql.ops.query.generate
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [lrsql.spec.reaction :as react-spec]))

(s/fdef generate
  :args (s/cat :cond-map (s/map-of :react-spec/condition-name
                         :react-spec/val)
               :statement map?))

(defn generate [cond->statement template]
  (walk/postwalk #(if (and (map? %)
                           (= (key (first %)) "$templatePath"))
                    (get-in cond->statement (val (first %)))
                    %)
                 template))
