(ns lrsql.util.database-test
  (:require [clojure.test :refer [deftest testing are]]
            [lrsql.system.util :refer [parse-db-props]]))

(deftest parse-db-props-test
  (testing "parsing DB property strings"
    (are [m s] (= m (parse-db-props s))
      {:foo "bar"} "foo=bar"
      {:foo "bar" :baz "qux"} "foo=bar&baz=qux"
      {:FOO "bar%3BBAZ%3Dqux"} "FOO=bar;BAZ=qux" ; Sorry H2
      {:currentSchema "lrsql%2Cpublic"} "currentSchema=lrsql,public"
      {:currentSchema "lrsql%2Cpublic"} "currentSchema=lrsql%2Cpublic"
      ;; Taken from:
      ;; https://jdbc.postgresql.org/documentation/head/connect.html#connection-parameters
      {:options "-c+search_path%3Dlrsql%2Cpublic%2Cfoo%26bar"}
      "options=-c search_path=lrsql,public,foo%26bar"
      {:options "-c+search_path%3Dlrsql%2Cpublic%2Cfoo" :bar "baz"}
      "options=-c search_path=lrsql,public,foo&bar=baz"
      {:options "-c+search_path%3Dlrsql%2Cpublic%2Cpgcatalog+-c+statement_timeout%3D90000"}
      "options=-c%20search_path=lrsql,public,pgcatalog%20-c%20statement_timeout=90000")))
