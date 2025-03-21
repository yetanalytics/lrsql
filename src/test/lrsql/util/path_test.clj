(ns lrsql.util.path-test
  (:require [clojure.test :refer [deftest testing are use-fixtures]]
            [com.yetanalytics.pathetic :as pa]
            [lrsql.util.path :as p]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)

(deftest path->sqlpath-string-test
  (testing "Property path to JSONPath-like string for SQL"
    (are [input output]
         (= (p/path->sqlpath-string input)
            output)
      []
      "$"

      ["object" "id"]
      "$.object.id"

      ["context" "contextActivities" "parent" 0 "id"]
      "$.context.contextActivities.parent[0].id"

      ["context" "extensions" "https://www.google.com/array"]
      "$.context.extensions.\"https://www.google.com/array\"")))

(deftest path->jsonpath-vec-test
  (testing "Property path to parsed JSONPath vector"
    (are [input output]
         (= [(p/path->jsonpath-vec input)]
            (pa/parse-paths output))
      []
      "$"
      
      ["object" "id"]
      "$.object.id"
      
      ["context" "contextActivities" "parent" 0 "id"]
      "$.context.contextActivities.parent[0].id"
      
      ["context" "extensions" "https://www.google.com/array"]
      "$.context.extensions['https://www.google.com/array']")))

(deftest path->csv-header-test
  (testing "Property path to CSV header"
    (are [input output]
         (= (p/path->csv-header input)
            output)
      []
      ""
      
      ["object" "id"]
      "object_id"
      
      ["context" "contextActivities" "parent" 0 "id"]
      "context_contextActivities_parent_0_id"
      
      ["context" "extensions" "https://www.google.com/array"]
      "context_extensions_https://www.google.com/array")))
