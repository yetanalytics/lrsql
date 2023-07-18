(ns lrsql.util.reaction-test
  (:require [clojure.test :refer [deftest are]]
            [lrsql.util.reaction :as r]))

(deftest path->string-test
  (are [input output]
      (= (r/path->string input)
         output)
    []
    "$"

    [:object :id]
    "$.object.id"

    [:context :contextActivities :parent 0 :id]
    "$.context.contextActivities.parent[0].id"))

(def stmt-a
  {"actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/a"
             "objectType" "Activity"}
   "context" {"registration" "6fbd600f-d17c-4c74-801a-2ec2e53231f7"}})

(deftest statement-identity-test
  (are [identity-paths output]
      (= (r/statement-identity
          identity-paths
          stmt-a)
         output)
    [[:actor :mbox]]
    {[:actor :mbox] "mailto:bob@example.com"}

    [[:actor :mbox]
     [:context :registration]]
    {[:actor :mbox] "mailto:bob@example.com"
     [:context :registration] "6fbd600f-d17c-4c74-801a-2ec2e53231f7"}

    [[:actor :openid]
     [:context :registration]]
    nil

    [[:actor]]
    nil))
