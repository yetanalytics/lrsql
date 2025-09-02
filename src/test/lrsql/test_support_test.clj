(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.test-support :refer [fresh-sqlite-fixture]]))

(defn- get-db-name
  [config]
  (get-in config [:connection :database :db-name]))

(deftest fresh-db-fixture-test
  (testing "sets the db name for shared memory db"
    (is
     (= ":memory:?cache=shared"
        (get-db-name
         (fresh-sqlite-fixture
          #(read-config :test-sqlite-mem)))))))
