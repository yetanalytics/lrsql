(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.test-runner :as conf]
            [next.jdbc.date-time]
            [lrsql.system :as system]
            [lrsql.test-support :as support]))

(support/instrument-lrsql)

(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (system/system :test)
              sys' (component/start sys)]
          (is (conf/conformant?
               "-e" "http://localhost:8080/xapi" "-b" "-z" "-a"
               "-u" "username"
               "-p" "password"))
          (component/stop sys'))))))
