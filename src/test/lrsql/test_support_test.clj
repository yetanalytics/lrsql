(ns lrsql.test-support-test
  (:require [aero.core :refer [read-config]]
            [lrsql.test-support :refer :all]
            [clojure.test :refer :all]
            [lrsql.system :as system]))

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
         #(read-config "config.edn"
                       {:profile :test})))))))
  (testing "changes db-name"
    (is
     (not=
      (get-db-name
       (fresh-db-fixture
        #(read-config "config.edn"
                      {:profile :test})))
      (get-db-name
       (fresh-db-fixture
        #(read-config "config.edn"
                      {:profile :test}))))))
  (testing "sets system db-name"
    (is
     (not= "example"
           (get-in
            (fresh-db-fixture
             #(system/system))
            [:database :db-name])))))
