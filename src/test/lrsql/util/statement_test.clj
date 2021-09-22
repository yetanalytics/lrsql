(ns lrsql.util.statement-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.statement :as su]))

(def sample-id
  "030e001f-b32a-4361-b701-039a3d9fceb1")

(def sample-group
  {"mbox"       "mailto:sample.group@example.com"
   "name"       "Sample Group"
   "objectType" "Group"
   "member"     [{"mbox" "mailto:agent2@example.com"
                  "name" "Agent 2"}
                 {"mbox" "mailto:agent2@example.com"
                  "name" "Agent 2"}
                 {"mbox" "mailto:agent1@example.com"
                  "name" "Agent 1"}]})

(def sample-group-dissoc
  {"mbox"       "mailto:sample.group@example.com"
   "name"       "Sample Group"
   "objectType" "Group"
   "member"     [{"mbox" "mailto:agent2@example.com"
                  "name" "Agent 2"}
                 {"mbox" "mailto:agent1@example.com"
                  "name" "Agent 1"}]})

(def sample-verb
  {"id"      "http://adlnet.gov/expapi/verbs/answered"
   "display" {"en-US" "answered"}})

(def sample-verb-dissoc
  {"id" "http://adlnet.gov/expapi/verbs/answered"})

(def sample-activity
  {"id"         "http://www.example.com/tincan/activities/multipart"
   "objectType" "Activity"
   "definition" {"name"        {"en-US" "Multi Part Activity"}
                 "description" {"en-US" "Multi Part Activity Description"}}})

(def sample-activity-dissoc
  {"id"         "http://www.example.com/tincan/activities/multipart"
   "objectType" "Activity"})

(deftest statements-equal-test
  (testing "statement equality"
    (is (su/statement-equal?
         {"id"      sample-id
          "actor"   sample-group-dissoc
          "verb"    sample-verb-dissoc
          "object"  sample-activity-dissoc
          "context" {"instructor" sample-group-dissoc
                     "team"       sample-group-dissoc
                     "contextActivities"
                     {"category" [sample-activity-dissoc]
                      "parent"   [sample-activity-dissoc]
                      "grouping" [sample-activity-dissoc]
                      "other"    [sample-activity-dissoc]}}}
         {"id"      sample-id
          "actor"   sample-group
          "verb"    sample-verb
          "object"  sample-activity
          "context" {"instructor" sample-group
                     "team"       sample-group
                     "contextActivities"
                     {"category" [sample-activity]
                      "parent"   [sample-activity]
                      "grouping" [sample-activity]
                      "other"    [sample-activity]}}}))
    (is (su/statement-equal?
         {"id"      sample-id
          "actor"   sample-group
          "verb"    sample-verb
          "object"  sample-activity
          "context" {"instructor" sample-group
                     "team"       sample-group
                     "contextActivities"
                     {"category" [sample-activity]
                      "parent"   [sample-activity]
                      "grouping" [sample-activity]
                      "other"    [sample-activity]}}}
         {"id"      sample-id
          "actor"   sample-group
          "verb"    sample-verb
          "object"  sample-activity
          "context" {"instructor" sample-group
                     "team"       sample-group
                     "contextActivities"
                     {"category" [sample-activity
                                  sample-activity]
                      "parent"   [sample-activity
                                  sample-activity]
                      "grouping" [sample-activity
                                  sample-activity]
                      "other"    [sample-activity
                                  sample-activity]}}}))
    (testing "(with SubStatement)"
      (is (su/statement-equal?
           {"id"      sample-id
            "actor"   sample-group-dissoc
            "verb"    sample-verb-dissoc
            "object"  {"objectType" "SubStatement"
                       "actor"   sample-group-dissoc
                       "verb"    sample-verb-dissoc
                       "object"  sample-activity-dissoc
                       "context" {"instructor" sample-group-dissoc
                                  "team"       sample-group-dissoc
                                  "contextActivities"
                                  {"category" [sample-activity-dissoc]
                                   "parent"   [sample-activity-dissoc]
                                   "grouping" [sample-activity-dissoc]
                                   "other"    [sample-activity-dissoc]}}}}
           {"id"      sample-id
            "actor"   sample-group
            "verb"    sample-verb
            "object"  {"objectType" "SubStatement"
                       "actor"   sample-group
                       "verb"    sample-verb
                       "object"  sample-activity
                       "context" {"instructor" sample-group
                                  "team"       sample-group
                                  "contextActivities"
                                  {"category" [sample-activity
                                               sample-activity]
                                   "parent"   [sample-activity
                                               sample-activity]
                                   "grouping" [sample-activity
                                               sample-activity]
                                   "other"    [sample-activity
                                               sample-activity]}}}}))
      (is (su/statement-equal?
           {"id"      sample-id
            "actor"   sample-group
            "verb"    sample-verb
            "object"  {"objectType" "SubStatement"
                       "actor"   sample-group
                       "verb"    sample-verb
                       "object"  sample-activity
                       "context" {"instructor" sample-group
                                  "team"       sample-group
                                  "contextActivities"
                                  {"category" [sample-activity]
                                   "parent"   [sample-activity]
                                   "grouping" [sample-activity]
                                   "other"    [sample-activity]}}}}
           {"id"      sample-id
            "actor"   sample-group
            "verb"    sample-verb
            "object"  {"objectType" "SubStatement"
                       "actor"   sample-group
                       "verb"    sample-verb
                       "object"  sample-activity
                       "context" {"instructor" sample-group
                                  "team"       sample-group
                                  "contextActivities"
                                  {"category" [sample-activity
                                               sample-activity]
                                   "parent"   [sample-activity
                                               sample-activity]
                                   "grouping" [sample-activity
                                               sample-activity]
                                   "other"    [sample-activity
                                               sample-activity]}}}})))))
