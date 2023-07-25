(ns lrsql.util.reaction-test
  (:require [clojure.test :refer [deftest is are]]
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
