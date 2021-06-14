(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.system :as system]
            [lrsql.ops.command.auth :as auth-cmd]
            [lrsql.util :as u]
            [lrsql.test-support :as support]))

(t/use-fixtures :each support/fresh-db-fixture)

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (system/system :test)
              sys' (component/start sys)]
          (auth-cmd/insert-credential! ; Seed the credentials table
           (-> sys' :lrs :connection :conn-pool)
           {:primary-key    (u/generate-squuid)
            :api-key        "yeti"
            :secret-api-key "swordfish"
            :scope          "all"})
          (is (conf/conformant?
               "-e" "http://localhost:8080/xapi" "-b" "-z" "-a"
               "-u" "yeti"
               "-p" "swordfish"))
          (component/stop sys'))))))
