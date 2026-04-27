(ns lrsql.util.statement-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [lrsql.util.statement :as su]
            [lrsql.util :as u]
            [xapi-schema.spec :as xs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Constants
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

(def lrs-authority
  {"mbox"       "mailto:a@example.com"
   "objectType" "Agent"})

(def foreign-authority
  {"mbox"       "mailto:b@example.com"
   "objectType" "Agent"})

(def statement-1
  {"id"     sample-id
   "actor"  sample-group
   "verb"   sample-verb
   "object" sample-activity})

(def statement-2
  {"id"        sample-id
   "actor"     sample-group
   "verb"      sample-verb
   "object"    sample-activity
   "authority" foreign-authority})

(def statement-3
  {"id"          sample-id
   "actor"       sample-group
   "verb"        (assoc sample-verb "display" {})
   "object"      (assoc sample-activity
                        "definition"
                        {"name"        {}
                         "description" {}})
   "attachments" [(-> sample-attachment
                      (assoc "display" {})
                      (assoc "description" {}))]
   "context"     {}
   "result"      {}})

(def statement-4
  {"id"     sample-id
   "actor"  sample-group
   "verb"   sample-verb
   "object" (assoc sample-activity
                   "definition"
                   {;; Doesn't form a valid statement but
                    ;; we need to test these lang maps
                    "choices" [{"id"          "Choice"
                                "description" {}}]
                    "scale"   [{"id"          "Scale"
                                "description" {}}]
                    "source"  [{"id"          "Source"
                                "description" {}}]
                    "target"  [{"id"          "Target"
                                "description" {}}]
                    "steps"   [{"id"          "Step"
                                "description" {}}]})})

;; A version 2.0.0 version of statement-1
(def statement-5
  (-> statement-1
      (assoc "version" "2.0.0")
      (assoc "timestamp" "2025-08-29 15:16:24.816950Z")
      (assoc-in ["context" "contextAgents"] [{"objectType" "contextAgent"
                                              "agent" {"mbox" "mailto:a@example.com"
                                                       "objectType" "Agent"}}])
      (assoc-in ["context" "contextGroups"] [{"objectType" "contextGroup"
                                                "group" {"mbox" "mailto:g@example.com"
                                                          "objectType" "Group"
                                                          "member" [{"mbox" "mailto:m@example.com"
                                                                     "objectType" "Agent"}]}}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Max Limit ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest max-limit-test
  (testing "Ensure that query limit is itself limited by :statement-get-max"
    (is (= {:limit 25}
           (su/ensure-default-max-limit {:limit 25}
                                        {:stmt-get-max     50
                                         :stmt-get-default 10})))
    (is (= {:limit 10}
           (su/ensure-default-max-limit {}
                                        {:stmt-get-max     50
                                         :stmt-get-default 10})))
    (is (= {:limit 50}
           (su/ensure-default-max-limit {:limit 100}
                                        {:stmt-get-max     50
                                         :stmt-get-default 10}))))
  (testing "Ensure query limit for CSV download via :statement-get-max-csv"
    (is (= {:limit 25}
           (su/ensure-default-max-limit-csv {:limit 25}
                                            {})))
    (is (= {}
           (su/ensure-default-max-limit-csv {}
                                            {})))
    (is (= {:limit 100}
           (su/ensure-default-max-limit-csv {:limit 100}
                                            {:stmt-get-max-csv 1000000})))
    (is (= {:limit 1000000}
           (su/ensure-default-max-limit-csv {}
                                            {:stmt-get-max-csv 1000000})))))

;; Statement ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest prepare-statement-test
  (testing "adds timestamp, stored, version, and authority"
    (let [statement* (su/prepare-statement "1.0.3" lrs-authority statement-1)]
      (is (inst? (u/str->time (get statement* "timestamp"))))
      (is (inst? (u/str->time (get statement* "stored"))))
      (is (= "1.0.0" (get statement* "version")))
      (is (= lrs-authority (get statement* "authority")))))
  (testing "overwrites authority"
    (is (= lrs-authority
           (-> (su/prepare-statement "1.0.3" lrs-authority statement-2)
               (get "authority")))))
  (testing "dissocs empty maps"
    (is (= {"id"          sample-id
            "actor"       sample-group
            "verb"        sample-verb-dissoc
            "object"      sample-activity-dissoc
            "attachments" [(dissoc sample-attachment
                                   "display"
                                   "description")]}
           (-> (su/prepare-statement "1.0.3" lrs-authority statement-3)
               (dissoc "timestamp" "stored" "authority" "version"))))
    (is (= {"id"     sample-id
            "actor"  sample-group
            "verb"   sample-verb
            "object" (assoc sample-activity
                            "definition"
                            {"choices" [{"id" "Choice"}]
                             "scale"   [{"id" "Scale"}]
                             "source"  [{"id" "Source"}]
                             "target"  [{"id" "Target"}]
                             "steps"   [{"id" "Step"}]})}
           (-> (su/prepare-statement "1.0.3" lrs-authority statement-4)
               (dissoc "timestamp" "stored" "authority" "version"))))))

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

;; CSV ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest statements->csv-seq-test
  (testing "Turn statements seq into CSV seq"
    (let [headers    [["id"]
                      ["actor" "mbox"]
                      ["verb" "id"]
                      ["object" "id"]]
          statements (lazy-cat [statement-1
                                statement-2]
                               [statement-3
                                statement-4])
          stream     (su/statements->csv-seq headers statements)]
      (is (not (realized? stream)))
      (is (= ["id" "actor_mbox" "verb_id" "object_id"]
             (first stream)))
      (is (= [sample-id
              (get sample-group "mbox")
              (get sample-verb "id")
              (get sample-activity "id")]
             (first (-> stream rest))
             (first (-> stream rest rest))
             (first (-> stream rest rest rest))
             (first (-> stream rest rest rest rest))))
      (is (nil? (first (-> stream rest rest rest rest rest))))
      (is (realized? stream)))))

;; Versioning ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest convert-200-to-103-test
  (testing "Convert 2.0.0 statement to 1.0.3"
    (is (= statement-1
           (su/convert-200-to-103 statement-1)))
    (binding [xs/*xapi-version* "1.0.3"]
      (is (not (s/valid? ::xs/statement
                statement-5)))
      (is (s/valid? ::xs/statement
               (su/convert-200-to-103 statement-5))))))

(deftest strict-version-result-test
  (testing "Convert statements get result to strict version"
    (testing "single statement result"
      (binding [xs/*xapi-version* "1.0.3"]
        (is (s/valid? ::xs/statement
               (get (su/strict-version-result
                     {:statement statement-5})
                    :statement)))))
    (testing "multiple statement result"
      (binding [xs/*xapi-version* "1.0.3"]
        (is (s/valid? ::xs/statements
               (get-in
                (su/strict-version-result
                 {:statement-result
                  {:statements [statement-5]}})
                [:statement-result :statements])))))))
