(ns lrsql.reaction.protocol-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.reaction.protocol :as rp]
            [lrsql.util :as u]
            [lrsql.test-constants :as tc]
            [lrsql.test-support :as support]
            [next.jdbc :as jdbc]))

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

(use-fixtures :each support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(deftest react-to-statement-test
  (let [sys                   (support/test-system
                               :conf-overrides
                               {[:lrs :enable-reactions] false})
        sys'                  (component/start sys)
        {:keys [lrs reactor]} sys'
        ds                    (-> sys' :lrs :connection :conn-pool)]
    (try
      (testing "Processes simple reaction"
        ;; Add a reaction
        (let [{reaction-id
               :result}  (adp/-create-reaction
                          lrs "reaction-0" tc/simple-reaction-ruleset true)
              trigger-id (u/str->uuid
                          (get tc/reaction-stmt-b "id"))
              ;; Add statements
              _          (doseq [s [tc/reaction-stmt-a
                                    tc/reaction-stmt-b]]
                           (Thread/sleep 100)
                           (lrsp/-store-statements lrs tc/auth-ident [s] []))
              ;; React to last statement
              {[reaction-s-id] :statement-ids}
              (rp/-react-to-statement reactor trigger-id)]
          (testing "New statement added"
            (is (= {:statement-result
                    {:statements
                     [{"id"    reaction-s-id
                       "actor" {"mbox" "mailto:bob@example.com"},
                       "verb"  {"id" "https://example.com/verbs/completed"},
                       "object"
                       {"id"         "https://example.com/activities/a-and-b",
                        "objectType" "Activity"}}
                      tc/reaction-stmt-b
                      tc/reaction-stmt-a]
                     :more ""}
                    :attachments []}
                   (-> (lrsp/-get-statements
                        lrs
                        tc/auth-ident
                        {}
                        [])
                       ;; Remove LRS fields
                       (update-in
                        [:statement-result :statements]
                        #(mapv remove-props %))))))
          (testing "New statement reaction metadata"
            (is (= [#:xapi_statement{:reaction_id reaction-id
                                     :trigger_id  trigger-id}]
                   (jdbc/execute!
                    ds
                    ["SELECT reaction_id, trigger_id
                      FROM xapi_statement
                      WHERE statement_id = ?"
                     (u/str->uuid reaction-s-id)]))))))
      (finally
        (component/stop sys')))))

(deftest react-to-statement-error-test
  (let [sys                   (support/test-system
                               :conf-overrides
                               {[:lrs :enable-reactions] false})
        sys'                  (component/start sys)
        {:keys [lrs reactor]} sys']
    (try
      (testing "Stores reaction errors"
        ;; Add a reaction with a bad template
        (let [bad-ruleset
              (assoc
               tc/simple-reaction-ruleset
               :template
               ;; Template with invalid path
               {"actor"  {"mbox" {"$templatePath" ["x" "actor" "mbox"]}}
                "verb"   {"id" "https://example.com/verbs/completed"}
                "object" {"id"         "https://example.com/activities/a-and-b"
                          "objectType" "Activity"}})
              ;; Create bad reaction
              {reaction-id
               :result}  (adp/-create-reaction
                          lrs
                          "reaction-bad"
                          bad-ruleset
                          true)
              trigger-id (u/str->uuid
                          (get tc/reaction-stmt-b "id"))
              ;; Add statements
              _          (doseq [s [tc/reaction-stmt-a
                                    tc/reaction-stmt-b]]
                           (Thread/sleep 100)
                           (lrsp/-store-statements lrs tc/auth-ident [s] []))]
          (testing "No statement id results"
            (is (= {:statement-ids []}
                   (rp/-react-to-statement reactor trigger-id))))
          (testing "No statement added"
            (is (= {:statement-result
                    {:statements
                     [tc/reaction-stmt-b
                      tc/reaction-stmt-a]
                     :more ""}
                    :attachments []}
                   (-> (lrsp/-get-statements
                        lrs
                        tc/auth-ident
                        {}
                        [])
                       ;; Remove LRS fields
                       (update-in
                        [:statement-result :statements]
                        #(mapv remove-props %))))))
          (testing "Error is retrievable"
            (is (= [{:id      reaction-id
                     :title   "reaction-bad"
                     :ruleset bad-ruleset
                     :active  false
                     :error
                     {:type    "ReactionTemplateError",
                      :message "No value found at [\"x\" \"actor\" \"mbox\"]"}}]
                   (mapv
                    #(dissoc % :created :modified)
                    (adp/-get-all-reactions lrs)))))))
      (finally
        (component/stop sys')))))
