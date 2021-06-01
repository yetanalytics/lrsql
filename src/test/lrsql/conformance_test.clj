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
  #{"XAPI-00154" ;; TODO: broken by stmt-get-max/default
    ;; An LRS’s Statement API upon processing a successful GET
    ;; request with neither a “statementId” nor a
    ;; “voidedStatementId” parameter, returns code 200 OK and a
    ;; StatementResult Object.
    "XAPI-00162"
    ;; An LRS&'s Statement API processes a successful GET
    ;; request using a parameter (such as stored time) which
    ;; includes a voided statement and unvoided statements targeting
    ;; the voided statement. The API must return 200 Ok and the
    ;; statement result object, containing statements which target a
    ;; voided statement, but not the voided statement itself.
    "XAPI-00229"
    ;; An LRS's State API, rejects a POST request if the
    ;; document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00232"
    ;; An LRS's State API, rejects a POST request if the
    ;; document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00254"
    ;; The Activity Object must contain all available information
    ;; about an activity from any statements who target the same
    ;; “activityId”. For example, LRS accepts two statements each
    ;; with a different language description of an activity using
    ;; the exact same “activityId”. The LRS must return both
    ;; language descriptions when a GET request is made to the
    ;; Activities endpoint for that “activityId”.
    "XAPI-00278"
    ;; An LRS's Agent Profile API, rejects a POST request if
    ;; the document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00281"
    ;; An LRS's Agent Profile API, rejects a POST request if
    ;; the document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00309"
    ;; An LRS's Activity Profile API, rejects a POST request if
    ;; the document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00313"
    ;; An LRS's Activity Profile API, rejects a POST request if
    ;; the document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00314"
    ;; An LRS must reject, with 400 Bad Request, a POST request to
    ;; the Activity Profile API which contains name/value pairs with
    ;; invalid JSON and the Content-Type header is "application/json
    })

(def stateful-failures
  "XAPI conformance codes that fail when run with other tests"
  #{"XAPI-00164"
    ;; The Statements within the "statements" property will correspond
    ;; to the filtering criterion sent in with the GET request
    })

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
