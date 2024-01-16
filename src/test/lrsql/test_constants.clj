(ns lrsql.test-constants)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reactions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def simple-reaction-ruleset
  {:identityPaths [["actor" "mbox"]]
   :template
   {"actor"   {"mbox" {"$templatePath" ["a" "actor" "mbox"]}}
    "verb"    {"id" "https://example.com/verbs/completed"}
    "object"  {"id"         "https://example.com/activities/a-and-b"
               "objectType" "Activity"}
    "context" {"extensions" {"https://example.com/foo" nil}}}
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

(def reaction-stmt-a
  {"id"     "6fbd600f-d17c-4c74-801a-2ec2e53231f7"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/a"
             "objectType" "Activity"}
   "result" {"success" true}})

(def reaction-stmt-b
  {"id"     "c51d1628-ae4a-449f-8d8d-13d57207f468"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/b"
             "objectType" "Activity"}
   "result" {"success" true}})

;; Same actor, wrong activity
(def reaction-stmt-c
  {"id"     "5716d2c3-1ed3-4646-9475-a6b3f3dc5d66"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/c"
             "objectType" "Activity"}
   "result" {"success" true}})

;; Different actor, same action as a
(def reaction-stmt-d
  {"id"      "da38014b-371d-4549-8f9f-e05193b89998"
   "actor"   {"mbox" "mailto:alice@example.com"}
   "verb"    {"id" "https://example.com/verbs/completed"}
   "object"  {"id"         "https://example.com/activities/a"
              "objectType" "Activity"}
   "result"  {"success" true}
   "context" {"extensions"
              {"https://example.com/array"  ["foo" "bar" "baz"]
               "https://example.com/number" 200}}})
