(ns lrsql.test-constants)

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
