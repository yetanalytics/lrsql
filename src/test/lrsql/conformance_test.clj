(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once support/instrumentation-fixture)

(t/use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest conformance-test
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (support/test-system)
              sys' (component/start sys)
              pre  (-> sys' :webserver :config :url-prefix)
              url  (str "http://localhost:8080" pre)]
          (try
            (testing "1.0.3"
              (is (conf/conformant?
                   "-e" url "-b" "-z" "-a"
                   "-u" "username"
                   "-p" "password"
                   "-x" "1.0.3")))
            (testing "2.0.0"
              (is (conf/conformant?
                   "-e" url "-b" "-z" "-a"
                   "-u" "username"
                   "-p" "password"
                   "-x" "2.0.0")))
            (finally
              (component/stop sys'))))))))
