(ns lrsql.ops.query.reaction-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [lrsql.ops.query.reaction :as qr]
            [lrsql.test-support :as support]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- remove-props
  "Remove properties added by `prepare-statement`."
  [statement]
  (dissoc statement
          "timestamp"
          "stored"
          "authority"
          "version"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stmt-a
  {"id"     "6fbd600f-d17c-4c74-801a-2ec2e53231f7"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/a"
             "objectType" "Activity"}
   "result" {"success" true}})

(def stmt-b
  {"id"     "c51d1628-ae4a-449f-8d8d-13d57207f468"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/b"
             "objectType" "Activity"}
   "result" {"success" true}})

;; Same actor, wrong activity
(def stmt-c
  {"actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/c"
             "objectType" "Activity"}
   "result" {"success" true}})

;; Different actor, same action as b
(def stmt-d
  {"actor"  {"mbox" "mailto:alice@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/b"
             "objectType" "Activity"}
   "result" {"success" true}})

(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

(deftest query-reaction-test
  (let [sys   (support/test-system)
        sys'  (component/start sys)
        lrs   (-> sys' :lrs)
        bk    (:backend lrs)
        ds    (-> sys' :lrs :connection :conn-pool)]
    (lrsp/-store-statements lrs auth-ident [stmt-a] [])
    (lrsp/-store-statements lrs auth-ident [stmt-b] [])
    (lrsp/-store-statements lrs auth-ident [stmt-c] [])
    (lrsp/-store-statements lrs auth-ident [stmt-d] [])
    (try
      (testing "Returns relevant statements"
        (let [query-result (qr/query-reaction
                            bk ds
                            {:trigger-id (get stmt-b "id")
                             :conditions
                             {:a
                              {:and
                               [{:path [:object :id]
                                 :op   :eq
                                 :val  "https://example.com/activities/a"}
                                {:path [:verb :id]
                                 :op   :eq
                                 :val  "https://example.com/verbs/completed"}
                                {:path [:result :success]
                                 :op   :eq
                                 :val  true}]}
                              :b
                              {:and
                               [{:path [:object :id]
                                 :op   :eq
                                 :val  "https://example.com/activities/b"}
                                {:path [:verb :id]
                                 :op   :eq
                                 :val  "https://example.com/verbs/completed"}
                                {:path [:result :success]
                                 :op   :eq
                                 :val  true}
                                {:path [:timestamp]
                                 :op   :gt
                                 :ref  {:condition :a, :path [:timestamp]}}]}}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a b]}] query-result]
            (is (= (remove-props a) stmt-a))
            (is (= (remove-props b) stmt-b)))))
      (finally (component/stop sys')))))
