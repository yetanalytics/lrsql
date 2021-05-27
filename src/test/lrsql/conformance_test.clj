(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]))

(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (let [;; chomp the logs for the system itself
          _ (log/log-capture!
             'lrsql.conformance-test
             :debug
             :debug)
          sys (system/system)
          sys' (component/start sys)
          ;; run test suite w/o bail
          {:keys [logs]} (conf/run-test-suite
                          "-e" "http://localhost:8080/xapi" "-z")
          ;; stop capturing logs so we don't mess with test output
          _ (log/log-uncapture!)
          ;; explode the logs, keeping :depth
          tests (support/tests-seq logs)
          code-set (support/req-code-set
                    tests)]
      (is
       (= #{"XAPI-00183"
            "XAPI-00217"
            "XAPI-00288"
            "XAPI-00278"
            "XAPI-00282"
            "XAPI-00192"
            "XAPI-00281"
            "XAPI-00274"
            "XAPI-00322"
            "XAPI-00234"
            "XAPI-00233"
            "XAPI-00313"
            "XAPI-00269"
            "XAPI-00126"
            "XAPI-00220"
            "XAPI-00164"
            "XAPI-00162"
            "XAPI-00290"
            "XAPI-00188"
            "XAPI-00254"
            "XAPI-00232"
            "XAPI-00279"
            "XAPI-00154"
            "XAPI-00229"
            "XAPI-00280"
            "XAPI-00125"
            "XAPI-00310"
            "XAPI-00184"
            "XAPI-00308"
            "XAPI-00259"
            "XAPI-00127"
            "XAPI-00314"
            "XAPI-00309"}
          code-set))
      (component/stop sys'))))
