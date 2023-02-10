(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.test-support :refer [test-system
                                        fresh-h2-fixture
                                        current-db
                                        by-db]]
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

(deftest by-db-test
  (testing "db backend dispatch"
    (by-db
     :h2       (testing "h2 detection"
                 (is (= current-db :h2)))
     :sqlite   (testing "sqlite detection"
                 (is (= current-db :sqlite)))
     :postgres (testing "postgres detection"
                 (is (= current-db :postgres)))
     nil       (testing "unset current-db detection"
                 (is (= current-db nil)))
     :foo      (testing "this never runs"
                 (is false))))
  (testing ":default arg"
    (by-db
     :foo      (testing "this also never runs"
                 (is false))
     :default  (testing "this always runs"
                 (is true)))))
