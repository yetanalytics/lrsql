(ns lrsql.util.reaction-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lrsql.util :as u]
            [lrsql.util.reaction :as r]
            [lrsql.spec.statement :as ss]))

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
  {"actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/a"
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
    {["actor" "mbox"] "mailto:bob@example.com"
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

(def simple-reaction-ruleset
  {:identity-paths [["actor" "mbox"]]
   :conditions
   {:a
    {:and
     [{:path ["object" "id"]
       :op   "eq"
       :val  "https://example.com/activities/a"}
      {:path ["verb" "id"]
       :op   "eq"
       :val  "https://example.com/verbs/completed"}
      {:path ["result" "success"]
       :op   "eq"
       :val  true}]}
    :b
    {:and
     [{:path ["object" "id"]
       :op   "eq"
       :val  "https://example.com/activities/b"}
      {:path ["verb" "id"]
       :op   "eq"
       :val  "https://example.com/verbs/completed"}
      {:path ["result" "success"]
       :op   "eq"
       :val  true}
      {:path ["timestamp"]
       :op   "gt"
       :ref  {:condition "a", :path ["timestamp"]}}]}}})

(deftest reaction-ruleset-serde-test
  (testing "Serde round-trip"
    (is (= simple-reaction-ruleset
           (-> simple-reaction-ruleset
               r/serialize-ruleset
               r/deserialize-ruleset)))))
