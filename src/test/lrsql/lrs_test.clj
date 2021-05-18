(ns lrsql.lrs-test
  (:require [clojure.test :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [clojure.data.json :as json]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]))

(def stmt-1
  {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent"
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
                           "homepage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" "Voided"}
   "object" {"objectType" "StatementRef"
             "id"         "030e001f-b32a-4361-b701-039a3d9fceb1"}})

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
   "actor"       {"mbox"       "mailto:sample.agent.4@example.com"
                  "name"       "Sample Agent 4"
                  "objectType" "Agent"}
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

(defn- assert-in-mem-db
  []
  (when (not= "h2:mem" (:db-type env))
    (throw (ex-info "Test can only be run on in-memory H2 database!"
                    {:kind    ::non-mem-db
                     :db-type (:db-type env)}))))

(defn- drop-all!
  "Drop all tables in the db, in preparation for adding them again.
   DO NOT RUN THIS DURING PRODUCTION!!!"
  [tx]
  (doseq [cmd ["DROP TABLE IF EXISTS xapi_statement"
               "DROP TABLE IF EXISTS agent"
               "DROP TABLE IF EXISTS activity"
               "DROP TABLE IF EXISTS attachment"
               "DROP TABLE IF EXISTS statement_to_agent"
               "DROP TABLE IF EXISTS statement_to_activity"
               "DROP TABLE IF EXISTS statement_to_attachment"]]
    (jdbc/execute! tx [cmd])))

(defn- remove-props
  "Remove properties added by `input/prepare-statement`."
  [statement]
  (-> statement
      (dissoc "timestamp")
      (dissoc "stored")
      (dissoc "authority")
      (dissoc "version")))

(defn- remove-props-res
  "Apply `remove-props` to a StatementResult object."
  [query-res]
  (update query-res :statements (partial map remove-props)))

(deftest test-lrs-protocol-fns
  (let [_     (assert-in-mem-db)
        sys   (system/system)
        sys'  (component/start sys)
        lrs   (:lrs sys')
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        id-4  (get stmt-4 "id")
        ts    "3000-01-01T01:00:00Z" ; Date far into the future
        agt-1 (-> stmt-1 (get "actor") (json/write-str))
        vrb-1 (get-in stmt-1 ["verb" "id"])
        act-1 (get-in stmt-1 ["object" "id"])
        act-4 (get-in stmt-4 ["object" "id"])]
    (testing "statement insertions"
      (is (= {:statement-ids [id-1]}
             (lrsp/-store-statements lrs {} [stmt-1] [])))
      (is (= {:statement-ids [id-2 id-3]}
             (lrsp/-store-statements lrs {} [stmt-2 stmt-3] [])))
      (is (= {:statement-ids [id-4]}
             (lrsp/-store-statements lrs {} [stmt-4] [stmt-4-attach]))))
    (testing "statement ID queries"
      ;; Statement ID queries
      (is (= {:statement stmt-1}
             (-> (lrsp/-get-statements lrs {} {:voidedStatementId id-1} {})
                 (update :statement remove-props))))
      (is (= {:statement stmt-2}
             (-> (lrsp/-get-statements lrs {} {:statementId id-2} {})
                 (update :statement remove-props))))
      (is (= {:statement stmt-3}
             (-> (lrsp/-get-statements lrs {} {:statementId id-3} {})
                 (update :statement remove-props)))))
    (testing "statement property queries"
      (is (= {:statement-result {:statements [] :more ""}
              :attachments      []}
             (lrsp/-get-statements lrs {} {:since ts} {})))
      (is (= {:statement-result {:statements [stmt-1 stmt-2 stmt-3 stmt-4] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:until ts} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent agt-1} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:agent agt-1 :related_agents true} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:verb vrb-1} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:activity act-1} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props)))))
      (is (= {:statement-result {:statements [stmt-1 stmt-3] :more ""}
              :attachments      []}
             (-> (lrsp/-get-statements lrs {} {:activity act-1 :related_activities true} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))))))
    (testing "querying with attachments"
      (is (= {:statement-result {:statements [stmt-4] :more ""}
              :attachments      [(update stmt-4-attach :content #(String. %))]}
             (-> (lrsp/-get-statements lrs {} {:activity act-4 :attachments true} {})
                 (update-in [:statement-result :statements]
                            (partial map remove-props))
                 (update-in [:attachments]
                            vec)
                 (update-in [:attachments 0 :content]
                            #(String. %))))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-all! tx))
    (component/stop sys')))

(defn- drop-doc-tables!
  [tx]
  (doseq [cmd ["DROP TABLE IF EXISTS state_document"
               "DROP TABLE IF EXISTS agent_profile_document"
               "DROP TABLE IF EXISTS activity_profile_document"]]
    (jdbc/execute! tx [cmd])))

(def doc-id-params
  {:stateId    "some-id"
   :activityId "https://example.org/activity-type"
   :agent      "{\"mbox\":\"mailto:example@example.org\"}"})

(def doc-1
  "{\"foo\":1,\"bar\":2}")

(def doc-2
  "{\"foo\":10}")

(deftest test-document-fns
  (let [_     (assert-in-mem-db)
        sys   (system/system)
        sys'  (component/start sys)
        lrs   (:lrs sys')]
    (testing "document insertion"
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 doc-id-params
                                 (.getBytes doc-1)
                                 false)))
      (is (= {}
             (lrsp/-set-document lrs
                                 {}
                                 doc-id-params
                                 (.getBytes doc-2)
                                 true))))
    (testing "document query"
      (is (= {:contents        "{\"foo\":10,\"bar\":2}"
              :content-length 18
              :content-type   "application/octet-stream" ; TODO
              :id             "some-id"}
             (-> (lrsp/-get-document lrs {} doc-id-params)
                 (dissoc :updated)
                 (update :contents #(String. %))))))
    (testing "document ID query"
      (is (= {:document-ids ["some-id"]}
             (lrsp/-get-document-ids lrs
                                     {}
                                     (dissoc doc-id-params :stateId)))))
    (testing "document deletion"
      (is (= {}
             (lrsp/-delete-document lrs
                                    {}
                                    doc-id-params)))
      (is (= {:document-ids []}
             (lrsp/-get-document-ids lrs
                                     {}
                                     (dissoc doc-id-params :stateId)))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-doc-tables! tx))
    (component/stop sys')))
