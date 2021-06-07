(ns lrsql.util.util-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-uuid]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.util :as util]
            [lrsql.util.statement :as us]))

(deftest squuid-test
  (testing "squuid gentests"
    (is (nil? (check-validate `util/generate-squuid*)))
    (is (nil? (check-validate `util/generate-squuid))))
  (testing "squuid monotonicity"
    (is
     (loop [squuid-seq  (repeatedly 10000 util/generate-squuid)
            squuid-seq' (rest squuid-seq)]
       (if (empty? squuid-seq')
         true
         (let [prev-squuid (first squuid-seq)
               next-squuid (first squuid-seq')]
           (if (clj-uuid/uuid< prev-squuid next-squuid)
             (recur squuid-seq' (rest squuid-seq'))
             false))))))
  (testing "squuid monotonicity (lex sort)"
    (let [squuid-seq   (repeatedly 1000 util/generate-squuid)
          squuid-seq'  (->> squuid-seq
                           (map util/uuid->str)
                           sort)
          squuid-seq'' (map util/str->uuid squuid-seq')]
      (is (every? (partial re-matches xsr/UuidRegEx) squuid-seq'))
      (is (every? (fn [[u1 u2]] (clj-uuid/uuid= u1 u2))
                  (map (fn [u1 u2] [u1 u2]) squuid-seq squuid-seq''))))))

(deftest dissoc-statement-properties-test
  (testing "dissoc non-immutable statement properties"
    (= {"actor"   {"mbox"       "mailto:sample.group@example.com"
                   "name"       "Sample Group"
                   "objectType" "Group"
                   "member"     #{{"mbox" "mailto:agent2@example.com"
                                   "name" "Agent 2"}
                                  {"mbox" "mailto:agent1@example.com"
                                   "name" "Agent 1"}}}
        "verb"    {"id" "http://adlnet.gov/expapi/verbs/answered"}
        "object"  {"id"         "http://www.example.com/tincan/activities/multipart"
                   "objectType" "Activity"}
        "context" {"contextActivities"
                   {"other"
                    #{{"id"         "http://www.example.com/tincan/activities/multipart"
                       "objectType" "Activity"
                       "definition" {"name"        {"en-US" "Multi Part Activity"}
                                     "description" {"en-US" "Multi Part Activity Description"}}}}}}}
       (us/dissoc-statement-properties
        {"id"      "030e001f-b32a-4361-b701-039a3d9fceb1"
         "actor"   {"mbox"       "mailto:sample.group@example.com"
                    "name"       "Sample Group"
                    "objectType" "Group"
                    "member"     [{"mbox" "mailto:agent2@example.com"
                                   "name" "Agent 2"}
                                  {"mbox" "mailto:agent2@example.com"
                                   "name" "Agent 2"}
                                  {"mbox" "mailto:agent1@example.com"
                                   "name" "Agent 1"}]}
         "verb"    {"id"      "http://adlnet.gov/expapi/verbs/answered"
                    "display" {"en-US" "answered"}}
         "object"  {"id"         "http://www.example.com/tincan/activities/multipart"
                    "objectType" "Activity"
                    "definition" {"name"        {"en-US" "Multi Part Activity"}
                                  "description" {"en-US" "Multi Part Activity Description"}}}
         "context" {"contextActivities"
                    {"other"
                     [{"id"         "http://www.example.com/tincan/activities/multipart"
                       "objectType" "Activity"
                       "definition" {"name"        {"en-US" "Multi Part Activity"}
                                     "description" {"en-US" "Multi Part Activity Description"}}}
                      {"id"         "http://www.example.com/tincan/activities/multipart"
                       "objectType" "Activity"
                       "definition" {"name"        {"en-US" "Multi Part Activity"}
                                     "description" {"en-US" "Multi Part Activity Description"}}}]}}}))))
