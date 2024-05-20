(ns lrsql.util.statement-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.statement :as su]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def sample-attachment
  {"usageType"   "http://example.com/attachment-usage/test"
   "display"     {"en-US" "A test attachment"}
   "description" {"en-US" "A test attachment (description)"}
   "contentType" "text/plain"
   "length"      27
   "sha2"        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest prepare-statement-test
  (let [lrs-authority     {"mbox"       "mailto:a@example.com"
                           "objectType" "Agent"}
        foreign-authority {"mbox"       "mailto:b@example.com"
                           "objectType" "Agent"}
        ;; Statements
        statement-1 {"id"     sample-id
                     "actor"  sample-group
                     "verb"   sample-verb
                     "object" sample-activity}
        statement-2 {"id"        sample-id
                     "actor"     sample-group
                     "verb"      sample-verb
                     "object"    sample-activity
                     "authority" foreign-authority}
        statement-3 {"id"          sample-id
                     "actor"       sample-group
                     "verb"        (assoc sample-verb "display" {})
                     "object"      (assoc sample-activity
                                          "definition"
                                          {"name"        {}
                                           "description" {}
                                           ;; Doesn't form a valid statement but
                                           ;; we need to test these lang maps
                                           "choices"     [{"name"        {}
                                                           "description" {}}]
                                           "scale"       [{"name"        {}
                                                           "description" {}}]
                                           "source"      [{"name"        {}
                                                           "description" {}}]
                                           "target"      [{"name"        {}
                                                           "description" {}}]
                                           "steps"       [{"name"        {}
                                                           "description" {}}]})
                     "attachments" [(-> sample-attachment
                                        (assoc "display" {})
                                        (assoc "description" {}))]
                     "context"     {}
                     "result"      {}}]
    (testing "adds timestamp, stored, version, and authority"
      (let [statement* (su/prepare-statement lrs-authority statement-1)]
        (is (inst? (u/str->time (get statement* "timestamp"))))
        (is (inst? (u/str->time (get statement* "stored"))))
        (is (= su/xapi-version (get statement* "version")))
        (is (= lrs-authority (get statement* "authority")))))
    (testing "overwrites authority"
      (is (= lrs-authority
             (-> (su/prepare-statement lrs-authority statement-2)
                 (get "authority")))))
    (testing "dissocs empty maps"
      (is (= {"id"          sample-id
              "actor"       sample-group
              "verb"        sample-verb-dissoc
              "object"      sample-activity-dissoc
              "attachments" [(dissoc sample-attachment
                                     "display"
                                     "description")]}
             (-> (su/prepare-statement lrs-authority statement-3)
                 (dissoc "timestamp" "stored" "authority" "version")))))))

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
