(ns lrsql.ops.util.reaction-test
  (:require [clojure.test :refer [deftest testing is use-fixtures are]]
            [lrsql.ops.util.reaction :as ur]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]))

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

(use-fixtures :once support/instrumentation-fixture)
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
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset    tc/simple-reaction-ruleset
                             :trigger-id (u/str->uuid (get stmt-b "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:bob@example.com"}})]
          ;; unambiguous, finds only a single row with a and b
          (is (= 1 (count query-result)))
          (let [[{:keys [a b]}] query-result]
            (is (= stmt-a (remove-props a)))
            (is (= stmt-b (remove-props b))))))
      (testing "Works w/o identity"
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset
                             (merge tc/simple-reaction-ruleset
                                    {:identity-paths []})
                             :trigger-id         (u/str->uuid (get stmt-b "id"))
                             :statement-identity {}})]
          ;; ambiguous, finds a and b but ALSO d and b
          (is (= 2 (count query-result)))))
      (testing "JSON containment"
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset
                             (merge tc/simple-reaction-ruleset
                                    {:conditions
                                     {:a
                                      {:and
                                       [{:path ["context"
                                                "extensions"
                                                "https://example.com/array"]
                                         :op   "contains"
                                         :val  "bar"}]}}})
                             :trigger-id (u/str->uuid (get stmt-d "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= stmt-d (remove-props a))))))
      (testing "Numeric types"
        ;; If it is just using lex, "200" > "1000"
        ;; Therefore we make sure it can compare numbers correctly
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset
                             (merge tc/simple-reaction-ruleset
                                    {:conditions
                                     {:a
                                      {:and
                                       [{:path ["context"
                                                "extensions"
                                                "https://example.com/number"]
                                         :op   "lt"
                                         :val  1000}]}}})
                             :trigger-id (u/str->uuid (get stmt-d "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= stmt-d (remove-props a))))))
      (finally (component/stop sys')))))

(deftest query-active-reactions-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (-> sys' :lrs)
        bk   (:backend lrs)
        ds   (-> sys' :lrs :connection :conn-pool)]

    ;; Create an active reaction
    (adp/-create-reaction lrs tc/simple-reaction-ruleset true)
    ;; Create an inactive reaciton
    (adp/-create-reaction lrs tc/simple-reaction-ruleset false)

    (try
      (testing "Finds only active reactions"
        (is (= [{:ruleset tc/simple-reaction-ruleset}]
                 (->> (ur/query-active-reactions bk ds)
                      (map #(select-keys % [:ruleset]))))))
      (finally (component/stop sys')))))

(deftest query-reaction-history-test
  (let [sys        (support/test-system)
        sys'       (component/start sys)
        lrs        (-> sys' :lrs)
        bk         (:backend lrs)
        ds         (-> sys' :lrs :connection :conn-pool)
        [reaction-0-id
         reaction-1-id
         reaction-2-id]
        (map
         :result
         (repeatedly 3
                     #(adp/-create-reaction lrs tc/simple-reaction-ruleset true)))
        [a-id
         b-id
         c-id
         d-id]     (for [stmt [stmt-a stmt-b stmt-c stmt-d]]
                     (u/str->uuid (get stmt "id")))]

    ;; store a statements with chained reaciton data
    (lrsp/-store-statements lrs
                            auth-ident
                            [stmt-a
                             (ru/add-reaction-metadata
                              stmt-b
                              reaction-0-id
                              a-id)
                             (ru/add-reaction-metadata
                              stmt-c
                              reaction-1-id
                              b-id)
                             (ru/add-reaction-metadata
                              stmt-d
                              reaction-2-id
                              c-id)]
                            [])
    (try
      (testing "Finds the reaction-history of each statement"
        (are [stmt-id reactions]
            (= {:result reactions}
               (ur/query-reaction-history
                bk ds {:statement-id stmt-id}))
          a-id #{}
          b-id #{reaction-0-id}
          c-id #{reaction-0-id reaction-1-id}
          d-id #{reaction-0-id reaction-1-id reaction-2-id}))
      (finally (component/stop sys')))))
