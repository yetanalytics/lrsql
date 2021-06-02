(ns lrsql.lrs-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [next.jdbc    :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]
            [lrsql.test-support :as support]))

(def stmt-0
  {"id"     "5c9cbcb0-18c0-46de-bed1-c622c03163a1"
   "actor"  {"mbox"       "mailto:sample.foo@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-1
  {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent 1"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart"
             "objectType" "Activity"
             "definition" {"name"        {"en-US" "Multi Part Activity"}
                           "description" {"en-US" "Multi Part Activity Description"}}}})

(def stmt-2
  {"id"     "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homePage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" "Voided"}
   "object" {"objectType" "StatementRef"
             "id"         "5c9cbcb0-18c0-46de-bed1-c622c03163a1"}})

(def stmt-3
  (-> stmt-1
      (assoc "id" "708b3377-2fa0-4b96-9ff1-b10208b599b1")
      (assoc "actor" {"openid"     "https://example.org"
                      "name"       "Sample Agent 3"
                      "objectType" "Agent"})
      (assoc-in ["context" "instructor"] (get stmt-1 "actor"))
      (assoc-in ["object" "id"] "http://www.example.com/tincan/activities/multipart-2")
      (assoc-in ["context" "contextActivities" "other"] [(get stmt-1 "object")])))

(def stmt-4
  {"id"          "e8477a8d-786c-48be-a703-7c8ec7eedee5"
   "actor"       {"mbox"       "mailto:sample.group.4@example.com"
                  "name"       "Sample Group 4"
                  "objectType" "Group"
                  "member"     [{"mbox" "mailto:member1@example.com"
                                 "name" "Group Member 1"}
                                {"mbox" "mailto:member2@example.com"
                                 "name" "Group Member 2"}
                                {"mbox" "mailto:member3@example.com"
                                 "name" "Group Member 4"}]}
   "verb"        {"id"      "http://adlnet.gov/expapi/verbs/attended"
                  "display" {"en-US" "attended"}}
   "object"      {"id"         "http://www.example.com/meetings/occurances/34534"
                  "definition" {"extensions"  {"http://example.com/profiles/meetings/activitydefinitionextensions/room"
                                               {"name" "Kilby"
                                                "id"   "http://example.com/rooms/342"}}
                                "name"        {"en-GB" "example meeting"
                                               "en-US" "example meeting"}
                                "description" {"en-GB" "An example meeting that happened on a specific occasion with certain people present."
                                               "en-US" "An example meeting that happened on a specific occasion with certain people present."}
                                "type"        "http://adlnet.gov/expapi/activities/meeting"
                                "moreInfo"    "http://virtualmeeting.example.com/345256"}
                  "objectType" "Activity"}
   "attachments" [{"usageType"   "http://example.com/attachment-usage/test"
                   "display"     {"en-US" "A test attachment"}
                   "description" {"en-US" "A test attachment (description)"}
                   "contentType" "text/plain"
                   "length"      27
                   "sha2"        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"}]})

(def stmt-4-attach
  {:content     (.getBytes "here is a simple attachment")
   :contentType "text/plain"
   :length      27
   :sha2        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"})

(defn- drop-all!
  "Drop all tables in the db, in preparation for adding them again.
   DO NOT RUN THIS DURING PRODUCTION!!!"
  [tx]
  (doseq [cmd [;; Drop document tables
               "DROP TABLE IF EXISTS state_document"
               "DROP TABLE IF EXISTS agent_profile_document"
               "DROP TABLE IF EXISTS activity_profile_document"
               ;; Drop statement tables
               "DROP TABLE IF EXISTS statement_to_statement"
               "DROP TABLE IF EXISTS statement_to_activity"
               "DROP TABLE IF EXISTS statement_to_actor"
               "DROP TABLE IF EXISTS attachment"
               "DROP TABLE IF EXISTS activity"
               "DROP TABLE IF EXISTS actor"
               "DROP TABLE IF EXISTS xapi_statement"]]
    (jdbc/execute! tx [cmd])))

(defn- remove-props
  "Remove properties added by `input/prepare-statement`."
  [statement]
  (-> statement
      (dissoc "timestamp")
      (dissoc "stored")
      (dissoc "authority")
      (dissoc "version")))

(use-fixtures :each support/fresh-db-fixture)

(deftest test-statement-fns
  (let [_     (support/assert-in-mem-db)
        sys   (system/system)
        sys'  (component/start sys)
        lrs   (:lrs sys')
        id-0  (get stmt-0 "id")
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        id-4  (get stmt-4 "id")
        ts    "3000-01-01T01:00:00Z" ; Date far into the future
        agt-1 (-> stmt-1 (get "actor") (dissoc "name"))
        grp-4 (-> stmt-4 (get "actor") (dissoc "name"))
        mem-4 (-> stmt-4 (get-in ["actor" "member" 0]) (dissoc "name"))
        vrb-1 (get-in stmt-1 ["verb" "id"])
        act-1 (get-in stmt-1 ["object" "id"])
        act-4 (get-in stmt-4 ["object" "id"])]
    (testing "statement insertions"
      (is (= {:statement-ids [id-0]}
             (lrsp/-store-statements lrs {} [stmt-0] [])))
      (is (= {:statement-ids [id-1 id-2 id-3]}
             (lrsp/-store-statements lrs {} [stmt-1 stmt-2 stmt-3] [])))
      (is (= {:statement-ids [id-4]}
             (lrsp/-store-statements lrs {} [stmt-4] [stmt-4-attach]))))
    (testing "statement ID queries"
      ;; Statement ID queries
      (is (= {:statement stmt-0}
             (-> (lrsp/-get-statements lrs {} {:voidedStatementId id-0} #{})
                 (update :statement remove-props))))
      (is (= {:statement stmt-0}
             (-> (lrsp/-get-statements lrs {} {:voidedStatementId id-0 :format "canonical"} #{"en-US"})
                 (update :statement remove-props))))
      (is (= {:statement
              {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
               "actor"  {"objectType" "Agent"
                         "mbox"       "mailto:sample.agent@example.com"}
               "verb"   {"id" "http://adlnet.gov/expapi/verbs/answered"}
               "object" {"id" "http://www.example.com/tincan/activities/multipart"}}}
             (-> (lrsp/-get-statements lrs {} {:statementId id-1 :format "ids"} #{})
                 (update :statement remove-props))))
      (is (= {:statement stmt-2}
             (-> (lrsp/-get-statements lrs {} {:statementId id-2} #{})
                 (update :statement remove-props))))
      (is (= {:statement stmt-3}
             (-> (lrsp/-get-statements lrs {} {:statementId id-3} #{})
                 (update :statement remove-props)))))
    (testing "statement property queries"
      (is (= {:statement-result {:statements [] :more ""}
              :attachments      []}
             (lrsp/-get-statements lrs {} {:since ts} #{})))
      (is (= {:statement-result {:statements [stmt-1 stmt-2 stmt-3 stmt-4] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:until ts} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent agt-1} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent agt-1 :related_agents true} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-4] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent grp-4} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-4] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent mem-4} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:verb vrb-1} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:activity act-1} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:activity act-1 :related_activities true} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))))))
    (testing "querying with limits"
      (is (= {:statement-result
              {:statements [stmt-1 stmt-2]
               :more "http://localhost:8080/xapi/statements?limit=2&from="}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:limit 2} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))
                 (update-in [:statement-result :more]
                            #(->> % (re-matches #"(.*from=).*") second)))))
      (is (= {:statement-result
              {:statements [stmt-1 stmt-2]
               :more "http://localhost:8080/xapi/statements?limit=2&ascending=true&from="}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:limit 2 :ascending true} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))
                 (update-in [:statement-result :more]
                            #(->> % (re-matches #"(.*from=).*") second)))))
      (is (= {:statement-result {:statements [stmt-3 stmt-4] :more ""}
              :attachments      []}
             (let [more
                   (-> (lrsp/-get-statements lrs {} {:limit 2 :ascending true} #{})
                       (get-in [:statement-result :more]))
                   from
                   (->> more (re-matches #".*from=(.*)") second)]
               (-> (lrsp/-get-statements lrs {} {:limit 2 :ascending true :from from} #{})
                   (update-in [:statement-result :statements]
                              (partial map remove-props)))))))
    (testing "querying with attachments"
      (is (= {:statement-result {:statements [stmt-4] :more ""}
              :attachments      [(update stmt-4-attach :content #(String. %))]}
             (-> (lrsp/-get-statements lrs {} {:activity act-4 :attachments true} #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))
                 (update-in [:attachments]
                            vec)
                 (update-in [:attachments 0 :content]
                            #(String. %))))))

    (testing "querying with attachments (single)"
      (is (= {:statement    stmt-4
              :attachments  [(update stmt-4-attach :content #(String. %))]}
             (-> (lrsp/-get-statements lrs {} {:statementId
                                               (get stmt-4 "id")
                                               :attachments true} #{})
                 (update    :statement
                            remove-props)
                 (update-in [:attachments]
                            vec)
                 (update-in [:attachments 0 :content]
                            #(String. %))))))
    (testing "agent query"
      (is (= {:person {"objectType" "Person"
                       "name" ["Sample Agent 1"]
                       "mbox" ["mailto:sample.agent@example.com"]}}
             (lrsp/-get-person lrs {} {:agent agt-1}))))
    (testing "activity query"
      (is (= {:activity {"id" "http://www.example.com/tincan/activities/multipart"}}
             #_{:activity {"id"         "http://www.example.com/tincan/activities/multipart"
                           "objectType" "Activity"
                           "definition" {"name"        {"en-US" "Multi Part Activity"}
                                         "description" {"en-US" "Multi Part Activity Description"}}}}
             (lrsp/-get-activity lrs {} {:activityId act-1}))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-all! tx))
    (component/stop sys')))


(def stmt-1'
  {"id"     "00000000-0000-0000-0000-000000000001"
   "actor"  {"mbox"       "mailto:sample.0@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-2'
  {"id"     "00000000-0000-0000-0000-000000000002"
   "actor"  {"mbox"       "mailto:sample.1@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"objectType" "StatementRef"
             "id" "00000000-0000-0000-0000-000000000001"}})

(def stmt-3'
  {"id"     "00000000-0000-0000-0000-000000000003"
   "actor"  {"mbox"       "mailto:sample.2@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"objectType" "StatementRef"
             "id" "00000000-0000-0000-0000-000000000002"}})

(deftest test-statement-ref-fns
  (let [_     (support/assert-in-mem-db)
        sys   (system/system)
        sys'  (component/start sys)
        lrs   (:lrs sys')]
    (testing "statement insertions"
      (is (= {:statement-ids ["00000000-0000-0000-0000-000000000001"
                              "00000000-0000-0000-0000-000000000002"
                              "00000000-0000-0000-0000-000000000003"]}
             (lrsp/-store-statements lrs {} [stmt-1' stmt-2' stmt-3'] []))))
    (testing "statement queries"
      (is (= {:statement-result {:statements [stmt-1' stmt-2' stmt-3'] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs
                                       {}
                                       {:agent {"mbox" "mailto:sample.0@example.com"
                                                "objectType" "Agent"}}
                                       #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1' stmt-2' stmt-3'] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs
                                       {}
                                       {:activity "http://www.example.com/tincan/activities/multipart"}
                                       #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1' stmt-2' stmt-3'] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs
                                       {}
                                       {:verb "http://adlnet.gov/expapi/verbs/answered"}
                                       #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs
                                       {}
                                       {:since "3000-01-01T01:00:00Z"}
                                       #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1']}
              :attachments      []}
             (-> (lrsp/-get-statements lrs
                                       {}
                                       {:activity "http://www.example.com/tincan/activities/multipart"
                                        :limit 1}
                                       #{})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))
                 (update :statement-result dissoc :more)))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-all! tx))
    (component/stop sys')))

(def state-id-params
  {:stateId    "some-id"
   :activityId "https://example.org/activity-type"
   :agent      {"mbox" "mailto:example@example.org"}})

(def state-doc-1
  {:content-length 17
   :content-type   "application/json"
   :contents       (.getBytes "{\"foo\":1,\"bar\":2}")})

(def state-doc-2
  {:content-length 10
   :content-type   "application/json"
   :contents       (.getBytes "{\"foo\":10}")})

(def agent-prof-id-params
  {:profileId "https://example.org/some-profile"
   :agent     {"mbox" "mailto:foo@example.org"
               "name" "Foo Bar"}})

(def agent-prof-doc
  {:content-length 16
   :content-type   "text/plain"
   :contents       (.getBytes "Example Document")})

(def activity-prof-id-params
  {:profileId  "https://example.org/some-profile"
   :activityId "https://example.org/some-activity"})

(def activity-prof-doc
  {:content-length 18
   :content-type  "text/plain"
   :contents      (.getBytes "Example Document 2")})

(deftest test-document-fns
  (let [_    (support/assert-in-mem-db)
        sys  (system/system)
        sys' (component/start sys)
        lrs  (:lrs sys')]
    (testing "document insertion"
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 state-id-params
                                 state-doc-1
                                 true)))
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 state-id-params
                                 state-doc-2
                                 true)))
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 agent-prof-id-params
                                 agent-prof-doc
                                 false)))
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 activity-prof-id-params
                                 activity-prof-doc
                                 false))))
    (testing "document query"
      (is (= {:document
              {:contents       "{\"foo\":10,\"bar\":2}"
               :content-length 18
               :content-type   "application/json"
               :id             "some-id"}}
             (-> (lrsp/-get-document lrs {} state-id-params)
                 (update :document dissoc :updated)
                 (update-in [:document :contents] #(String. %)))))
      (is (= {:document
              {:contents       "Example Document"
               :content-length 16
               :content-type   "text/plain"
               :id             "https://example.org/some-profile"}}
             (-> (lrsp/-get-document lrs {} agent-prof-id-params)
                 (update :document dissoc :updated)
                 (update-in [:document :contents] #(String. %)))))
      (is (= {:document
              {:contents       "Example Document 2"
               :content-length 18
               :content-type   "text/plain"
               :id             "https://example.org/some-profile"}}
             (-> (lrsp/-get-document lrs {} activity-prof-id-params)
                 (update :document dissoc :updated)
                 (update-in [:document :contents] #(String. %))))))
    (testing "document ID query"
      (is (= {:document-ids ["some-id"]}
             (lrsp/-get-document-ids lrs
                                     {}
                                     (dissoc state-id-params :stateId))))
      (is (= {:document-ids ["https://example.org/some-profile"]}
             (lrsp/-get-document-ids lrs
                                     {}
                                     (dissoc agent-prof-id-params
                                             :profileId))))
      (is (= {:document-ids ["https://example.org/some-profile"]}
             (lrsp/-get-document-ids lrs
                                     {}
                                     (dissoc activity-prof-id-params
                                             :profileId)))))
    (testing "document deletion"
      (is (= {}
             (lrsp/-delete-documents lrs
                                     {}
                                     (dissoc state-id-params :stateId))))
      (is (= {}
             (lrsp/-delete-document lrs
                                    {}
                                    agent-prof-id-params)))
      (is (= {}
             (lrsp/-delete-document lrs
                                    {}
                                    activity-prof-id-params)))
      (is (nil? (lrsp/-get-document lrs
                                    {}
                                    state-id-params)))
      (is (nil? (lrsp/-get-document lrs
                                    {}
                                    agent-prof-id-params)))
      (is (nil? (lrsp/-get-document lrs
                                    {}
                                    activity-prof-id-params))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-all! tx))
    (component/stop sys')))
