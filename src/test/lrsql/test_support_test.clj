(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.test-support :refer [test-system fresh-h2-fixture]]
            [lrsql.util :as u]))

(defn- get-db-name
  [config]
  (get-in config [:connection :database :db-name]))

(deftest fresh-db-fixture-test
  (testing "sets the db name to a random uuid"
    (is
     (uuid?
      (u/str->uuid
       (get-db-name
        (fresh-h2-fixture
         #(read-config :test-h2-mem)))))))
  (testing "changes db-name"
    (is
     (not=
      (get-db-name
       (fresh-h2-fixture
        #(read-config :test-h2-mem)))
      (get-db-name
       (fresh-h2-fixture
        #(read-config :test-h2-mem))))))
  (testing "sets system db-name"
    (is
     (not= "example"
           (get-in
            (fresh-h2-fixture
             #(test-system))
            [:connection :database :db-name])))))
