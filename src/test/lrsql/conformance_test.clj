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
            [clojure.tools.logging :as log]
            [clojure.set :as cset]
            [clojure.pprint :refer [pprint]]))

(def known-failures
  "XAPI conformance codes that we know fail in isolation"
  #{})

(def stateful-failures
  "XAPI conformance codes that fail when run with other tests"
  #{})

(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (testing "known failures"
      (doseq [code (sort known-failures)]
        (testing (format "requirement: %s" code)
          ;; invoke the fixture manually so we get a fresh db for each assert
          (support/fresh-db-fixture
           (fn []
             (let [;; chomp the logs for the system itself
                   _ (log/log-capture!
                      'lrsql.conformance-test
                      :debug
                      :debug)
                   sys (system/system)
                   sys' (component/start sys)
                   ;; run test suite w/o bail
                   conformant?
                   (binding [conf/report-sh-result (constantly nil)]
                     (conf/conformant?
                      "-e" "http://localhost:8080/xapi" "-z"
                      "-g" code))
                   ;; stop capturing logs so we don't mess with test output
                   _ (log/log-uncapture!)]
               (is (not conformant?))
               (component/stop sys')))))))
    (testing "regression"
      (let [_ (log/log-capture!
               'lrsql.conformance-test
               :debug
               :debug)
            sys (system/system)
            sys' (component/start sys)
            ;; run test suite w/o bail
            {:keys [logs]} (conf/run-test-suite
                            "-e" "http://localhost:8080/xapi" "-z")
            _ (log/log-uncapture!)
            tests (support/tests-seq logs)
            code-set (support/req-code-set
                      tests)
            regressions (cset/difference
                         code-set
                         known-failures
                         stateful-failures)]
        (is (empty? regressions))
        ;; print log information per regression
        (doseq [code regressions]
          (printf "\nfailing xapi code: %s\n" code)
          (println "logs:")
          (doseq [fail (support/filter-code
                        code
                        tests)]
            (pprint (conf/wrap-request-logs fail))))
        (component/stop sys')))))
