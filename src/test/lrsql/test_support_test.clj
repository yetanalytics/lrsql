(ns lrsql.test-support-test
  (:require [aero.core :refer [read-config]]
            [lrsql.test-support :refer :all]
            [clojure.test :refer :all]))

(defn- get-db-name
  [config]
  (get-in config [:database :db-name]))

(deftest fresh-db-fixture-test
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
