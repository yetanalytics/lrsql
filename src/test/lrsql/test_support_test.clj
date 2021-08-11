(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.test-support :refer [test-system fresh-db-fixture]]))

(defn- get-db-name
  [config]
  (get-in config [:database :db-name]))

(deftest fresh-db-fixture-test
  (testing "sets the db name to a random uuid"
    (is
     (uuid?
      (java.util.UUID/fromString
       (get-db-name
        (fresh-db-fixture
         #(read-config :test-h2-mem)))))))
  (testing "changes db-name"
    (is
     (not=
      (get-db-name
       (fresh-db-fixture
        #(read-config :test-h2-mem)))
      (get-db-name
       (fresh-db-fixture
        #(read-config :test-h2-mem))))))
  (testing "sets system db-name"
    (is
     (not= "example" ; TODO: this will come from env, check against that
           (get-in
            (fresh-db-fixture
             #(test-system))
            [:database :db-name])))))
