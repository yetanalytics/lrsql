(ns lrsql.util.statement-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.statement :as su]))

(deftest dissoc-statement-properties-test
  (testing "dissoc non-immutable statement properties"
    (is (= {"actor"   {"mbox"       "mailto:sample.group@example.com"
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
                           "objectType" "Activity"}}}}}
           (su/dissoc-statement-properties
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
                                         "description" {"en-US" "Multi Part Activity Description"}}}]}}})))))