(ns lrsql.lrs-test
  (:require [clojure.test :refer [deftest testing is]]
            #_[config.core  :refer [env]]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]))

(def stmt-1
  {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart"
             "objectType" "Activity"
             "definition" {"name"        {"en-US" "Multi Part Activity"}
                           "description" {"en-US" "Multi Part Activity Description"}}}})

(def stmt-2
  {"id"     "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homepage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" "Voided"}
   "object" {"objectType" "StatementRef"
             "id"         "030e001f-b32a-4361-b701-039a3d9fceb1"}})

;; TODO: Always ensure :db-type is h2:mem
;; TODO: Be able to run this test multiple times w/o repl restarts
(deftest test-lrs-protocol-fns
  (let [sys  (system/system)
        sys' (component/start sys)
        lrs  (:lrs sys')
        id-1 (get stmt-1 "id")
        id-2 (get stmt-2 "id")]
    (testing "insertions"
      (is (= [id-1] (lrsp/-store-statements lrs {} [stmt-1] [])))
      (is (= [id-2] (lrsp/-store-statements lrs {} [stmt-2] []))))
    (testing "queries"
      (is (= stmt-2
             (-> (lrsp/-get-statements lrs {} {:statementId id-2} {})
                 (dissoc "timestamp")
                 (dissoc "stored")
                 (dissoc "authority")
                 (dissoc "version")))))
    (component/stop sys')))
