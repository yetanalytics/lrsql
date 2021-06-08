(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [com.stuartsierra.component    :as component]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]))

#_(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (system/system :test)
              sys' (component/start sys)]
          (is (conf/conformant?
               "-e" "http://localhost:8080/xapi" "-b" "-z"))
          (component/stop sys'))))))
