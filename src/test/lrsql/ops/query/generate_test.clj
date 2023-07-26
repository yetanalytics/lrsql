(ns lrsql.ops.query.generate-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.ops.query.generate :as generate]))

(def fake-cond-map
  {"completed_a" {"id"     "6fbd600f-d17c-4c74-801a-2ec2e53231f7"
                  "actor"  {"mbox" "mailto:bob@example.com"
                            "madeUpAttr" "8"}
                  "verb"   {"id" "https://example.com/verbs/completed"}
                  "object" {"id"         "https://example.com/activities/a"
                            "objectType" "Activity"}
                  "result" [{"success" true} {"success" false}]}
   "completed_b"   {"id"     "c51d1628-ae4a-449f-8d8d-13d57207f468"
                    "actor"  {"mbox" "mailto:bob@example.com"}
                    "verb"   {"id" "https://example.com/verbs/completed"}
                    "object" {"id"         "https://example.com/activities/b"
                              "objectType" "Activity"}
                    "result" [{"success" false} {"success" true}]}})

(def fake-template {"actor" {"mbox" {"$templatePath" ["completed_a" "actor" "mbox"]}}
                    "verb"  "did"
                    "object"  "this"})

(deftest statement-generation-test
  (testing "Basic statement generation"
    (is (= (generate/generate
            fake-cond-map
            {"actor" {"mbox" {"$templatePath" ["completed_a" "actor" "mbox"]}}
             "verb"  "did"
             "object"  "this"})
           {"actor" {"mbox" "mailto:bob@example.com"}
            "verb"  "did"
            "object"  "this"})))

            
  (testing "Inclusion of multiple attributes"
      (is (= (generate/generate
              fake-cond-map
              {"actor" {"mbox" "mailto:bob@example.com"
                        "madeUpAttr" {"$templatePath" ["completed_a" "actor" "madeUpAttr"]}}
               "verb"  "did"
               "object"  "this"})
             {"actor" {"mbox" "mailto:bob@example.com"
                       "madeUpAttr" "8"}
              "verb"  "did"
              "object"  "this"})))
  (testing "non-string contents"
    (is (= (generate/generate
            fake-cond-map
            {"verb"   {"$templatePath" ["completed_b" "verb"]}})
           {"verb"  {"id" "https://example.com/verbs/completed"}})))

  (testing "generate statements patched together from different conditions"
    (is (= (generate/generate
            fake-cond-map
            {"actor"  {"$templatePath" ["completed_a" "actor"]}
             "verb"   "did"
             "object" {"$templatePath" ["completed_b" "object"]}})

           {"actor" {"mbox" "mailto:bob@example.com"
                     "madeUpAttr" "8"}
            "verb"  "did"
            "object" {"id" "https://example.com/activities/b"
                      "objectType" "Activity"}})))
  (testing "access by index"
        (is (= (generate/generate
            fake-cond-map
            {"a_first_result" {"$templatePath" ["completed_a" "result" 0 "success"]}
             "b_first_result" {"$templatePath" ["completed_b" "result" 0 "success"]}})
            {"a_first_result" true
             "b_first_result" false}))))

