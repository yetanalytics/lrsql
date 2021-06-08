(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]))

#_(t/use-fixtures :each support/fresh-db-fixture)

(defn- drop-all!
  "Drop all tables in the db, in preparation for adding them again.
   DO NOT RUN THIS DURING PRODUCTION!!!"
  [tx]
  (doseq [cmd [;; Drop document tables
               "DROP TABLE IF EXISTS state_document"
               "DROP TABLE IF EXISTS agent_profile_document"
               "DROP TABLE IF EXISTS activity_profile_document"
               ;; Drop statement tables
               "DROP TABLE IF EXISTS statement_to_statement"
               "DROP TABLE IF EXISTS statement_to_activity"
               "DROP TABLE IF EXISTS statement_to_actor"
               "DROP TABLE IF EXISTS attachment"
               "DROP TABLE IF EXISTS activity"
               "DROP TABLE IF EXISTS actor"
               "DROP TABLE IF EXISTS xapi_statement"]]
    (jdbc/execute! tx [cmd])))

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (binding [conf/*print-logs* true]
      (testing "no regressions"
        (let [sys  (system/system :test)
              sys' (component/start sys)]
          (is (conf/conformant?
               "-e" "http://localhost:8080/xapi" "-b" "-z"))
          (jdbc/with-transaction [tx (-> sys' :lrs :connection :conn-pool)]
            (drop-all! tx))
          (component/stop sys'))))))
