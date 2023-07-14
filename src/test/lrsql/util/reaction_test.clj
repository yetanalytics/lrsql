(ns lrsql.util.reaction-test
  (:require [clojure.test :refer [deftest is testing are]]
            [lrsql.util.reaction :as r]))

(deftest path->string-test
  (are [input output]
      (= (r/path->string input)
         output)
    []
    "$"

    [:object :id]
    "$.object.id"

    [:context :contextActivities :parent 0 :id]
    "$.context.contextActivities.parent[0].id"))
