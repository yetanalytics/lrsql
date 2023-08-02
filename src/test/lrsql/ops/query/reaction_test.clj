(ns lrsql.ops.query.reaction-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [lrsql.ops.query.reaction :as qr]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc]
            [com.stuartsierra.component :as component]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [com.yetanalytics.lrs.protocol :as lrsp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(deftest query-all-reactions-test
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
      (testing "Finds all reactions"
        (is (= [{:ruleset tc/simple-reaction-ruleset
                 :active  true}
                {:ruleset tc/simple-reaction-ruleset
                 :active  false}]
               (->> (qr/query-all-reactions bk ds)
                    (map #(select-keys % [:ruleset :active]))))))
      (finally (component/stop sys')))))

(deftest query-statement-reactions-valid-test
  (testing "valid reaction"
    (let [sys        (support/test-system)
          sys'       (component/start sys)
          lrs        (-> sys' :lrs)
          bk         (:backend lrs)
          ds         (-> sys' :lrs :connection :conn-pool)
          trigger-id (u/str->uuid (get tc/reaction-stmt-b "id"))
          {reaction-id
           :result}  (adp/-create-reaction
                      lrs tc/simple-reaction-ruleset true)]
      ;; Add statements
      (doseq [s [tc/reaction-stmt-a
                 tc/reaction-stmt-b]]
        (Thread/sleep 100)
        (lrsp/-store-statements lrs tc/auth-ident [s] []))
      (try
        (is (= {:result
                [{:reaction-id reaction-id
                  :trigger-id  trigger-id
                  :statement
                  {"actor" {"mbox" "mailto:bob@example.com"},
                   "verb"  {"id" "https://example.com/verbs/completed"},
                   "object"
                   {"id"         "https://example.com/activities/a-and-b",
                    "objectType" "Activity"}}}]}
               (qr/query-statement-reactions
                bk ds {:trigger-id trigger-id})))
        (finally (component/stop sys'))))))

(deftest query-statement-reactions-template-error-test
  (testing "Invalid template"
    (let [sys        (support/test-system)
          sys'       (component/start sys)
          lrs        (-> sys' :lrs)
          bk         (:backend lrs)
          ds         (-> sys' :lrs :connection :conn-pool)
          trigger-id (u/str->uuid (get tc/reaction-stmt-b "id"))
          {reaction-id
           :result}
          (adp/-create-reaction
           lrs
           (merge
            tc/simple-reaction-ruleset
            {:template
             ;; Template with invalid path
             {"actor"  {"mbox" {"$templatePath" ["x" "actor" "mbox"]}}
              "verb"   {"id" "https://example.com/verbs/completed"}
              "object" {"id"         "https://example.com/activities/a-and-b"
                        "objectType" "Activity"}}})
           true)]
      (try
        ;; Add statements
        (doseq [s [tc/reaction-stmt-a
                   tc/reaction-stmt-b]]
          (Thread/sleep 100)
          (lrsp/-store-statements lrs tc/auth-ident [s] []))
        (is (= {:result
                [{:reaction-id reaction-id
                  :trigger-id  trigger-id
                  :error       :lrsql.reaction.error/template}]}
               (qr/query-statement-reactions
                bk ds {:trigger-id trigger-id})))
        (finally (component/stop sys'))))))

(deftest query-statement-reactions-invalid-statement-error-test
  ;; Turn off instrumentation for this one
  ;; This will keep the instrumentation error from clobbering
  (support/unstrument-lrsql)
  (testing "Invalid template"
    (let [sys        (support/test-system)
          sys'       (component/start sys)
          lrs        (-> sys' :lrs)
          bk         (:backend lrs)
          ds         (-> sys' :lrs :connection :conn-pool)
          trigger-id (u/str->uuid (get tc/reaction-stmt-b "id"))
          {reaction-id
           :result}
          (adp/-create-reaction
           lrs
           (merge
            tc/simple-reaction-ruleset
            {:template
             ;; Template with invalid statement
             {"foo" {"$templatePath" ["a" "actor" "mbox"]}}})
           true)]
      (try
        ;; Add statements
        (doseq [s [tc/reaction-stmt-a
                   tc/reaction-stmt-b]]
          (Thread/sleep 100)
          (lrsp/-store-statements lrs tc/auth-ident [s] []))
        (is (= {:result
                [{:reaction-id reaction-id
                  :trigger-id  trigger-id
                  :error       :lrsql.reaction.error/invalid-statement}]}
               (qr/query-statement-reactions
                bk ds {:trigger-id trigger-id})))
        (finally (component/stop sys'))))))
