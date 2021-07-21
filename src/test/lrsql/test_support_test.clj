(ns lrsql.test-support-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.test-support :refer [h2-system fresh-db-fixture]]
            [lrsql.util :as u]))

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
         #(u/read-config :test)))))))
  (testing "changes db-name"
    (is
     (not=
      (get-db-name
       (fresh-db-fixture
        #(u/read-config :test)))
      (get-db-name
       (fresh-db-fixture
        #(u/read-config :test))))))
  (testing "sets system db-name"
    (is
     (not= "example" ; TODO: this will come from env, check against that
           (get-in
            (fresh-db-fixture
             #(h2-system))
            [:database :db-name])))))
