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

(deftest query-reaction-test
  (let [sys  (support/test-system
              :conf-overrides
              {[:lrs :enable-reactions] false})
        sys' (component/start sys)
        lrs  (-> sys' :lrs)
        bk   (:backend lrs)
        ds   (-> sys' :lrs :connection :conn-pool)]

    (doseq [s [tc/reaction-stmt-d
               tc/reaction-stmt-a
               tc/reaction-stmt-b
               tc/reaction-stmt-c]]
      (Thread/sleep 100)
      (lrsp/-store-statements lrs tc/auth-ident [s] []))

    (try
      (testing "Returns relevant statements"
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset    tc/simple-reaction-ruleset
                             :trigger-id (u/str->uuid
                                          (get tc/reaction-stmt-b "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:bob@example.com"}})]
          ;; unambiguous, finds only a single row with a and b
          (is (= 1 (count query-result)))
          (let [[{:keys [a b]}] query-result]
            (is (= tc/reaction-stmt-a (remove-props a)))
            (is (= tc/reaction-stmt-b (remove-props b))))))
      (testing "Works w/o identity"
        (let [query-result (ur/query-reaction
                            bk ds
                            {:ruleset
                             (merge tc/simple-reaction-ruleset
                                    {:identityPaths []})
                             :trigger-id         (u/str->uuid
                                                  (get tc/reaction-stmt-b "id"))
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
                             :trigger-id (u/str->uuid
                                          (get tc/reaction-stmt-d "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= tc/reaction-stmt-d (remove-props a))))))
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
                             :trigger-id (u/str->uuid
                                          (get tc/reaction-stmt-d "id"))
                             :statement-identity
                             {["actor" "mbox"] "mailto:alice@example.com"}})]
          (is (= 1 (count query-result)))
          (let [[{:keys [a]}] query-result]
            (is (= tc/reaction-stmt-d (remove-props a))))))
      (finally (component/stop sys')))))

(deftest query-reaction-history-test
  (let [sys    (support/test-system
                :conf-overrides
                {[:lrs :enable-reactions] false})
        sys'   (component/start sys)
        lrs    (-> sys' :lrs)
        bk     (:backend lrs)
        ds     (-> sys' :lrs :connection :conn-pool)
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
         d-id] (for [stmt [tc/reaction-stmt-a
                           tc/reaction-stmt-b
                           tc/reaction-stmt-c
                           tc/reaction-stmt-d]]
                     (u/str->uuid (get stmt "id")))]

    ;; store a statements with chained reaciton data
    (lrsp/-store-statements lrs
                            tc/auth-ident
                            [tc/reaction-stmt-a
                             (ru/add-reaction-metadata
                              tc/reaction-stmt-b
                              reaction-0-id
                              a-id)
                             (ru/add-reaction-metadata
                              tc/reaction-stmt-c
                              reaction-1-id
                              b-id)
                             (ru/add-reaction-metadata
                              tc/reaction-stmt-d
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
