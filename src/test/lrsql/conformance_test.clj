(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]))

(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (let [sys (system/system)
          sys' (component/start sys)]
      (is (conf/conformant?
           ;; TODO: match these to what you actually serve
           "-e" "http://localhost:8080/xapi" "-b" "-z"

           ;; zero in on specific tests using grep:
           "-g" "XAPI-00154|XAPI-00164" ;; failing post SQL-29
           ))
      (component/stop sys'))))
