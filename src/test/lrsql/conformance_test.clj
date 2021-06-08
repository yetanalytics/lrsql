(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
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
