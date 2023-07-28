(ns lrsql.ops.util.reaction-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [lrsql.ops.util.reaction :as qr]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc]
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
  {"id"     "5716d2c3-1ed3-4646-9475-a6b3f3dc5d66"
   "actor"  {"mbox" "mailto:bob@example.com"}
   "verb"   {"id" "https://example.com/verbs/completed"}
   "object" {"id"         "https://example.com/activities/c"
             "objectType" "Activity"}
   "result" {"success" true}})

;; Different actor, same action as a
(def stmt-d
  {"id"      "da38014b-371d-4549-8f9f-e05193b89998"
   "actor"   {"mbox" "mailto:alice@example.com"}
   "verb"    {"id" "https://example.com/verbs/completed"}
   "object"  {"id"         "https://example.com/activities/a"
              "objectType" "Activity"}
   "result"  {"success" true}
   "context" {"extensions"
              {"https://example.com/array"  ["foo" "bar" "baz"]
               "https://example.com/number" 200}}})

(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

(deftest query-reaction-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (-> sys' :lrs)
        bk   (:backend lrs)
        ds   (-> sys' :lrs :connection :conn-pool)]

    (doseq [s [stmt-d stmt-a stmt-b stmt-c]]
      (Thread/sleep 100)
      (lrsp/-store-statements lrs auth-ident [s] []))

    (try
      (testing "Returns relevant statements"
        (let [query-result (qr/query-reaction
                            bk ds
                            {:ruleset    tc/simple-reaction-ruleset
                             :trigger-id (get stmt-b "id")
                             :statement-identity
                             {["actor" "mbox"] "mailto:bob@example.com"}})]
          ;; unambiguous, finds only a single row with a and b
          (is (= 1 (count query-result)))
          (let [[{:keys [a b]}] query-result]
            (is (= stmt-a (remove-props a)))
            (is (= stmt-b (remove-props b))))))
      (testing "Works w/o identity"
        (let [query-result (qr/query-reaction
                            bk ds
                            {:ruleset
                             {:identity-paths []
                              :conditions
                              (:conditions tc/simple-reaction-ruleset)}
                             :trigger-id         (get stmt-b "id")
                             :statement-identity {}})]
          ;; ambiguous, finds a and b but ALSO d and b
          (is (= 2 (count query-result)))))
      (testing "JSON containment"
        (let [query-result (qr/query-reaction
                            bk ds
                            {:ruleset
                             {:identity-paths [["actor" "mbox"]]
                              :conditions
                              {:a
                               {:and
                                [{:path ["context"
                                         "extensions"
                                         "https://example.com/array"]
                                  :op   "contains"
                                  :val  "bar"}]}}}
                             :trigger-id (get stmt-d "id")
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= stmt-d (remove-props a))))))
      (testing "Numeric types"
        ;; If it is just using lex, "200" > "1000"
        ;; Therefore we make sure it can compare numbers correctly
        (let [query-result (qr/query-reaction
                            bk ds
                            {:ruleset
                             {:identity-paths [["actor" "mbox"]]
                              :conditions
                              {:a
                               {:and
                                [{:path ["context"
                                         "extensions"
                                         "https://example.com/number"]
                                  :op   "lt"
                                  :val  1000}]}}}
                             :trigger-id (get stmt-d "id")
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= stmt-d (remove-props a))))))
      (finally (component/stop sys')))))
