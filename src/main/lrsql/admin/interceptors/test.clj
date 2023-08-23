(ns lrsql.admin.interceptors.test
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.ops.command.reaction :as rc]
            [lrsql.ops.query.reaction   :as rq]
            [lrsql.admin.protocol :as adp]))

(def ruleset
  {:identity-paths [["actor" "mbox"]]
   :template
   {"actor"  {"mbox" {"$templatePath" ["a" "actor" "mbox"]}}
    "verb"   {"id" "https://example.com/verbs/completed"}
    "object" {"id"         "https://example.com/activities/a-and-b"
              "objectType" "Activity"}}
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


(def testy-boi
  "Delete the selected API key pair."
  (interceptor
   {:name ::testy-boi
    :enter
    (fn testy-boi [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            save-result (adp/-create-reaction lrs ruleset true)
            query-result (adp/-get-all-reactions lrs)
            _ (clojure.pprint/pprint ["query result" query-result])]
        (assoc ctx
               :response
               {:status 200 :body "bleh"})))}))
