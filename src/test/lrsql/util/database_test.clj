(ns lrsql.util.database-test
  (:require [clojure.test :refer [deftest testing are]]
            [lrsql.system.util :refer [parse-db-props]]))

(deftest parse-db-props-test
  (testing "parsing DB property strings"
    (are [m s] (= m (parse-db-props s))
      {:foo "bar"} "foo=bar"
      {:foo "bar" :baz "qux"} "foo=bar&baz=qux"
      {:currentSchema "lrsql%2Cpublic"} "currentSchema=lrsql,public"
      ;; Quoted strings
      {:foo "bar" :baz "qux"} "foo='bar'&baz=\"qux\""
      {:foo "bar" :baz "qux%26queen"} "foo='bar'&baz=\"qux&queen\""
      {:options "-c+search_path%3Dlrsql%2Cpublic%2Cfoo%26bar"}
      "options='-c search_path=lrsql,public,foo&bar'"
      {:options "-c+search_path%3Dlrsql%2Cpublic%2Cfoo%26bar"}
      "options=\"-c search_path=lrsql,public,foo&bar\""
      ;; NOTE: Percent encodings get encoded again
      {:currentSchema "lrsql%252Cpublic"} "currentSchema=lrsql%2Cpublic")))
