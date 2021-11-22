(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]))

;; Init

(support/instrument-lrsql)

(t/use-fixtures :each support/fresh-db-fixture)

;; Tests

(deftest conformance-test
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (support/test-system)
              sys' (component/start sys)]
          (is (conf/conformant?
               "-e" "http://localhost:8080/yet/xapi" "-b" "-z" "-a"
               "-u" "username"
               "-p" "password"))
          (component/stop sys'))))))
