(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.test-support :refer [fresh-sqlite-fixture]]))

(defn- get-db-name
  [config]
  (get-in config [:connection :database :db-name]))

(deftest fresh-db-fixture-test
  (testing "sets the db name to a random uuid"
    (is
     (= ":memory:"
        (get-db-name
         (fresh-sqlite-fixture
          #(read-config :test-sqlite-mem)))))))
