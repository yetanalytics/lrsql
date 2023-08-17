(ns lrsql.util.reaction-test
  (:require [clojure.test :refer [deftest testing is are use-fixtures]]
            [lrsql.util :as u]
            [lrsql.util.reaction :as r]
            [lrsql.spec.statement :as ss]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)

(deftest path->string-test
  (are [input output]
      (= output
         (r/path->string input))
    []
    "$"

    ["object" "id"]
    "$.\"object\".\"id\""

    ["context" "contextActivities" "parent" 0 "id"]
    "$.\"context\".\"contextActivities\".\"parent\"[0].\"id\""

    ["context" "extensions" "https://www.google.com/array"]
    "$.\"context\".\"extensions\".\"https://www.google.com/array\""))

(def stmt-a
  {"actor"   {"mbox" "mailto:bob@example.com"}
   "verb"    {"id" "https://example.com/verbs/completed"}
   "object"  {"id"         "https://example.com/activities/a"
              "objectType" "Activity"}
   "context" {"registration" "6fbd600f-d17c-4c74-801a-2ec2e53231f7"}})

(deftest statement-identity-test
  (are [identity-paths output]
      (= output
         (r/statement-identity
          identity-paths
          stmt-a))
    [["actor" "mbox"]]
    {["actor" "mbox"] "mailto:bob@example.com"}

    [["actor" "mbox"]
     ["context" "registration"]]
    {["actor" "mbox"]           "mailto:bob@example.com"
     ["context" "registration"] "6fbd600f-d17c-4c74-801a-2ec2e53231f7"}

    [["actor" "openid"]
     ["context" "registration"]]
    nil

    [["actor"]]
    nil))

(deftest add-reaction-metadata-test
  (let [reaction-id (u/generate-squuid)
        trigger-id  (u/generate-squuid)]
    (is (= {::ss/reaction-id reaction-id
            ::ss/trigger-id  trigger-id}
           (meta (r/add-reaction-metadata stmt-a reaction-id trigger-id))))))

(def fake-cond-map
  {:completed_a {"id"      "6fbd600f-d17c-4c74-801a-2ec2e53231f7"
                 "actor"   {"mbox" "mailto:bob@example.com"}
                 "verb"    {"id" "https://example.com/verbs/completed"}
                 "object"  {"id"         "https://example.com/activities/a"
                            "objectType" "Activity"}
                 "result"  {"success" true}
                 "context" {"extensions" {"https://example.com/someext"
                                          ["Alice" "Bob" "Connie"]}}}
   :completed_b {"id"     "c51d1628-ae4a-449f-8d8d-13d57207f468"
                 "actor"  {"mbox" "mailto:bob@example.com"}
                 "verb"   {"id" "https://example.com/verbs/completed"}
                 "object" {"id"         "https://example.com/activities/b"
                           "objectType" "Activity"}
                 "result" {"success" true}}})

(deftest statement-generation-test
  (testing "Basic statement generation"
    (is (= (r/generate-statement
            fake-cond-map
            {"actor"  {"mbox" {"$templatePath" ["completed_a" "actor" "mbox"]}}
             "verb"   {"id" "https://example.com/someverb"}
             "object" {"id" "https://example.com/someactivity"}})
           {"actor"  {"mbox" "mailto:bob@example.com"}
            "verb"   {"id" "https://example.com/someverb"}
            "object" {"id" "https://example.com/someactivity"}})))

  (testing "Inclusion of multiple attributes"
    (is (= (r/generate-statement
            fake-cond-map
            {"actor"  {"mbox" {"$templatePath" ["completed_a" "actor" "mbox"]}}
             "verb"   {"id" {"$templatePath" ["completed_b" "verb" "id"]}}
             "object" {"id" "https://example.com/someactivity"}})
           {"actor"  {"mbox" "mailto:bob@example.com"}
            "verb"   {"id" "https://example.com/verbs/completed"}
            "object" {"id" "https://example.com/someactivity"}})))
  (testing "non-string contents"
    (is (= (r/generate-statement
            fake-cond-map
            {"actor"  {"mbox" "mailto:bob@example.com"}
             "verb"   {"$templatePath" ["completed_b" "verb"]}
             "object" {"id" "https://example.com/someactivity"}})
           {"actor"  {"mbox" "mailto:bob@example.com"}
            "verb"   {"id" "https://example.com/verbs/completed"}
            "object" {"id" "https://example.com/someactivity"}})))
  (testing "access by index"
    (is (= (r/generate-statement
            fake-cond-map
            {"actor"  {"mbox" "mailto:bob@example.com"
                       "name" {"$templatePath" ["completed_a"
                                                "context"
                                                "extensions"
                                                "https://example.com/someext"
                                                1]}}
             "verb"   {"id" "https://example.com/someverb"}
             "object" {"id" "https://example.com/someactivity"}})
           {"actor"  {"mbox" "mailto:bob@example.com"
                      "name" "Bob"}
            "verb"   {"id" "https://example.com/someverb"}
            "object" {"id" "https://example.com/someactivity"}})))

  (testing "when no value at path, throws appropriate exception"
    (is (= (try (r/generate-statement
                 fake-cond-map
                 {"actor_birthday" {"$templatePath" ["completed_a" "actor" "birthday"]}})
                (catch Exception e (select-keys (Throwable->map e) [:data :cause])))
           {:data  {:type :lrsql.util.reaction/invalid-path
                    :path ["completed_a" "actor" "birthday"]}
            :cause "No value found at [\"completed_a\" \"actor\" \"birthday\"]"}))))
