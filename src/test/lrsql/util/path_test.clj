(ns lrsql.util.path-test
  (:require [clojure.test :refer [deftest testing are use-fixtures]]
            [lrsql.util.path :as p]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)

(deftest path->string-test
  (testing "Property path to JSONPath string"
    (are [input output]
         (= output
            (p/path->jsonpath-string input))
      []
      "$"

      ["object" "id"]
      "$.\"object\".\"id\""

      ["context" "contextActivities" "parent" 0 "id"]
      "$.\"context\".\"contextActivities\".\"parent\"[0].\"id\""

      ["context" "extensions" "https://www.google.com/array"]
      "$.\"context\".\"extensions\".\"https://www.google.com/array\"")))

(deftest path->csv-header-test
  (testing "Property path to CSV header"
    (are [input output]
         (= output
            (p/path->csv-header input))
      []
      ""
      
      ["object" "id"]
      "object_id"
      
      ["context" "contextActivities" "parent" 0 "id"]
      "context_contextActivities_parent_0_id"
      
      ["context" "extensions" "https://www.google.com/array"]
      "context_extensions_https://www.google.com/array")))
