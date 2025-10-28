(ns lrsql.lrs-test
  (:require [clojure.test   :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [clojure.walk                   :as walk]
            [com.stuartsierra.component     :as component]
            [com.yetanalytics.lrs.protocol  :as lrsp]
            [lrsql.admin.protocol           :as adp]
            [lrsql.test-support             :as support]
            [lrsql.test-constants           :as tc]
            [lrsql.util                     :as u]
            [xapi-schema.spec               :as xs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init Test Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Instrument
(use-fixtures :once support/instrumentation-fixture)

;; New DB config
(use-fixtures :each support/fresh-db-fixture)

(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- remove-props
  "Remove properties added by `lrsql.util.statement/prepare-statement`."
  [statement]
  (dissoc statement "timestamp" "stored" "authority" "version"))

(defn get-ss
  "Same as `lrsp/-get-statements` except that `remove-props` is applied
   on the results."
  [lrs auth-ident params ltags]
  (if (or (contains? params :statementId)
          (contains? params :voidedStatementId))
    (-> (lrsp/-get-statements lrs {} auth-ident params ltags)
        (update :statement remove-props))
    (-> (lrsp/-get-statements lrs {} auth-ident params ltags)
        (update-in [:statement-result :statements]
                   (partial map remove-props)))))

(defn- update-attachment-content
  [att]
  (update att :content u/bytes->str))

(defn- string-result-attachment-content
  [get-ss-result]
  (update get-ss-result
          :attachments
          (fn [atts] (mapv update-attachment-content atts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Some Statement language maps include Chinese text, in order to test
;; language maps with non-Unicode text.

;; Need to have a non-zero UUID version, or else xapi-schema gets angry

(def stmt-0
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor"  {"mbox"       "mailto:sample.foo@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"
                        "zh-CN" "回答了"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-1
  {"id"     "00000000-0000-4000-8000-000000000001"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent 1"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"
                        "zh-CN" "回答了"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart"
             "objectType" "Activity"
             "definition" {"type"        "http://www.example.com/activity-types/test"
                           "name"        {"en-US" "Multi Part Activity"
                                          "zh-CN" "多元部分Activity"}
                           "description" {"en-US" "Multi Part Activity Description"
                                          "zh-CN" "多元部分Activity的简述"}}}})

(def stmt-2
  {"id"     "00000000-0000-4000-8000-000000000002"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homePage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" {"en-US" "Voided"}}
   "object" {"objectType" "StatementRef"
             "id"         "00000000-0000-4000-8000-000000000000"}})

(def stmt-3
  (-> stmt-1
      (assoc "id" "00000000-0000-4000-8000-000000000003")
      (assoc "actor" {"openid"     "https://example.org"
                      "name"       "Sample Agent 3"
                      "objectType" "Agent"})
      (assoc-in ["context" "instructor"] (get stmt-1 "actor"))
      (assoc-in ["object" "id"] "http://www.example.com/tincan/activities/multipart-2")
      (assoc-in ["context" "contextActivities" "other"] [(get stmt-1 "object")])))

(def stmt-4
  {"id"          "00000000-0000-4000-8000-000000000004"
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
                  "display" {"en-US" "attended"
                             "zh-CN" "参加了"}}
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

(def stmt-5 (assoc stmt-4 "id" "00000000-0000-4000-8000-000000000006"))

(def stmt-6
  {"id"          "00000000-0000-4000-8000-000000000005"
   "actor"       {"mbox"       "mailto:sample.foo@example.com"
                  "objectType" "Agent"}
   "verb"        {"id"      "http://adlnet.gov/expapi/verbs/answered"
                  "display" {"en-US" "answered"}}
   "object"      {"id" "http://www.example.com/tincan/activities/multipart"}
   "attachments" [{"usageType"   "http://example.com/attachment-usage/test"
                   "display"     {"en-US" "A test attachment"}
                   "description" {"en-US" "A test attachment (description)"}
                   "contentType" "text/plain"
                   "length"      27
                   "sha2"        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"}
                  {"usageType"   "http://example.com/attachment-usage/test"
                   "display"     {"en-US" "A test attachment"}
                   "description" {"en-US" "A test attachment (description)"}
                   "contentType" "text/plain"
                   "length"      33
                   "sha2"        "7063d0a4cfa93373753ad2f5a6ffcf684559fb1df3c2f0473a14ece7d4edb06a"}
                  {"usageType"   "http://example.com/attachment-usage/test"
                   "display"     {"en-US" "A test attachment"}
                   "description" {"en-US" "A test attachment (description)"}
                   "contentType" "text/plain"
                   "length"      33
                   "sha2"        "7063d0a4cfa93373753ad2f5a6ffcf684559fb1df3c2f0473a14ece7d4edb06a"}]})

(def stmt-6-attach
  {:content     (.getBytes "here is a simple attachment")
   :contentType "text/plain"
   :length      33
   :sha2        "7063d0a4cfa93373753ad2f5a6ffcf684559fb1df3c2f0473a14ece7d4edb06a"})

;; Extremely long IRIs
(def stmt-7
  {"id"     "00000000-0000-4000-8000-000000000127"
   "actor"  {"account"    {"homePage" "https://www.example.com/users/h894hf8934hf8934h8934hf8934h89h89f3h894hf8934hf8943gh8f34h89fh8934h8f934h8f943h89f34h89f34h89f34h89f34h89fh348fh3489fh438fh8934hf880234hg89234gh8934gh8349gh8349gh8349gh8943hg8934gh8934hg8493hg843hg8934h89g34hg8943h8934h8g943h89g34h89g34h8934h89g34h89g34h89g34h89g34h89g34h89g34h8g34h89g34h89g34h89g34h8g43h89gh3489gh3489g934h89fh3489fh8349fh8934h89f34h89f34h89fh43834h89fh4893fh8349fh8934fh8934hf8934hf8934h89f34h89f34h89f34h89f34h89f34h89fh3489f",
                           "name"     "NothingToSeeHere"}
             "objectType" "Agent"}
   "verb"   {"id"         "http://example.com/verbs/24g890gh348h8934gh8349h89g34hg8934hg8934h3489gh8934hg3489h8g34h89g34h4389g34gh83h89gh8934hg894hgfuifbgnusrbgjrjkgbneruiognhuio34bnhuobgh4389gh48gh34g8493ghu34wenhgberuiohegr89h3458ghjerihertyuioernhjkogernguioh89gh348gh84ibhgiohsdrioghb349uih3489ghbu934hgi9erhjgiheru9gh943h89gh3894hu9ghu89werhguibhdsfuijgbnerjinuigernhuighjeriohjgioehuirgh4ioghjioerhg34uiogh349hgf3489gh34u89gh3489hg8943hg8934h89gh23489g34h89g34h89g34hg8934hg3489h4389gh3489hyg3489hg8934hg893h489gh3489gh89",
             "display"    {"en-US" "Very Normal Verb"}}
   "object" {"id"         "http://www.example.com/activities/38902fh23890fh2389fh238hf8923fh8239hf8923h829hf8923hf87923h89hf8h2389fh8239h238fh2389fh8923fh2389fh2389fh823fh3892fh8923hf2389hf8239fh8239fh8923h8392h823f9hf823h89f32hf8932h89f23h89f23h89f23h89f32h8923fh8f23hf23h823fh89f23h3892hf2389fh2389hf8932hf8923h89f3h8932hf893hf8923hf8932h238hf328hf8923h23f8ifh23uifh23uibfh23ubf23ifb23yi23bfyuifui23b23fuib3fui2b23fuifb23bfu32bfui23bui32bf23uibfui23bfui23bfui23bfui32bfui23bgh23uifbui23bfuib23uibf2ui3bfui23buifb23uibfu32b3uifbui"}})

;; xAPI 2.0 context Actors and Groups
(def stmt-8
  {"id"     "00000000-0000-4000-8000-000000000200"
   "actor"  {"mbox"       "mailto:sample.foo@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"
                        "zh-CN" "回答了"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}
   "context"
   {"contextAgents" [{"objectType" "contextAgent"
                      "agent"      {"mbox" "mailto:ctxagent@example.com"
                                    "objectType" "Agent"}}]
    "contextGroups" [{"objectType" "contextGroup"
                      "group"      {"mbox"   "mailto:ctxgroup@example.com"
                                    "objectType" "Group"
                                    "member" [{"mbox" "mailto:ctxmember@example.com"
                                                "objectType" "Agent"}]}}]}})

(deftest test-statement-fns
  (let [sys   (support/test-system)
        sys'  (component/start sys)
        lrs   (-> sys' :lrs)
        pre   (-> sys' :webserver :config :url-prefix)
        id-0  (get stmt-0 "id")
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        id-4  (get stmt-4 "id")
        id-7  (get stmt-7 "id")
        ts    "3000-01-01T01:00:00Z" ; Date far into the future
        agt-0 (-> stmt-0 (get "actor"))
        agt-1 (-> stmt-1 (get "actor") (dissoc "name"))
        grp-4 (-> stmt-4 (get "actor") (dissoc "name"))
        mem-4 (-> stmt-4 (get-in ["actor" "member" 0]) (dissoc "name"))
        vrb-1 (get-in stmt-1 ["verb" "id"])
        act-1 (get-in stmt-1 ["object" "id"])
        act-4 (get-in stmt-4 ["object" "id"])]
    (try
      (testing "empty statement insertions"
        (is (= {:statement-ids []}
               (lrsp/-store-statements lrs
                                       tc/ctx
                                       auth-ident [] [])))
        (is (= {:statement-result {:statements []
                                   :more       ""}
                :attachments      []}
               (get-ss lrs auth-ident {:limit 50} #{}))))

      (testing "statement insertions"
        (is (= {:statement-ids [id-0]}
               (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0] [])))
        (is (= {:statement-ids [id-1 id-2 id-3]}
               (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1 stmt-2 stmt-3] [])))
        (is (= {:statement-ids [id-4]}
               (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-4] [stmt-4-attach]))))

      (testing "statement conflicts"
        (is (= {:statement-ids []}
               (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1 stmt-1] [])))
        (testing "(verb display not part of Statement Immutability)"
          (let [stmt-1' (assoc-in stmt-1 ["verb" "display" "en-US"] "ANSWERED")]
            (is (= {:statement-ids []}
                   (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1'] [])))))
        (testing "(actor IFI is part of Statement Immutability)"
          ;; Neither statement should be inserted due to batch rollback, even
          ;; though stmt-b could be inserted by itself.
          (let [stmt-a (assoc-in stmt-1
                                 ["actor" "mbox"]
                                 "mailto:sample.agent.boo@example.com")
                stmt-b (assoc stmt-a
                              "id"
                              "00000000-0000-4000-8000-000000000010")]
            (is (= ::lrsp/statement-conflict
                   (-> (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-b stmt-a] [])
                       :error
                       ex-data
                       :type))))))

      (testing "statement ID queries"
        (is (= {:statement stmt-0}
               (get-ss lrs auth-ident {:voidedStatementId id-0} #{})))
        (is (= {:statement (update-in stmt-0 ["verb" "display"] dissoc "zh-CN")}
               (get-ss lrs
                       auth-ident
                       {:voidedStatementId id-0 :format "canonical"}
                       #{"en-US"})))
        (is (= {:statement
                {"id"     id-1
                 "actor"  {"objectType" "Agent"
                           "mbox"       "mailto:sample.agent@example.com"}
                 "verb"   {"id" "http://adlnet.gov/expapi/verbs/answered"}
                 "object" {"id" "http://www.example.com/tincan/activities/multipart"}}}
               (get-ss lrs auth-ident {:statementId id-1 :format "ids"} #{})))
        (is (= {:statement stmt-2}
               (get-ss lrs auth-ident {:statementId id-2} #{})))
        (is (= {:statement stmt-3}
               (get-ss lrs auth-ident {:statementId id-3} #{}))))

      (testing "statement property queries"
        ;; Also checks that no extra stmts were inserted on rolled-back inserts
        (is (= {:statement-result {:statements [stmt-4 stmt-3 stmt-2 stmt-1]
                                   :more       ""}
                :attachments      []}
               (get-ss lrs auth-ident {} #{})))
        (is (= {:statement-result {:statements [] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:since ts} #{})))
        (is (= {:statement-result {:statements [stmt-4 stmt-3 stmt-2 stmt-1]
                                   :more       ""}
                :attachments      []}
               (get-ss lrs auth-ident {:until ts} #{})))
        (is (= {:statement-result {:statements [stmt-1 stmt-2 stmt-3 stmt-4]
                                   :more       ""}
                :attachments      []}
               (get-ss lrs auth-ident {:until ts :ascending true} #{})))
        (is (= {:statement-result {:statements [stmt-1] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:agent agt-1} #{})))
        (is (= {:statement-result {:statements [stmt-3 stmt-1] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:agent agt-1 :related_agents true} #{})))
        (is (= {:statement-result {:statements [stmt-4] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:agent grp-4} #{})))
        (is (= {:statement-result {:statements [stmt-4] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:agent mem-4} #{})))

        ;; XAPI-00162 - stmt-2 shows up because it refers to a statement, stmt-0,
        ;; that meets the query criteria, even though stmt-0 was voided.
        (testing "apply voiding"
          ;; stmt-0 itself cannot be directly queried, since it was voided.
          ;; However, stmt-2 is returned since it was not voided.
          (is (= {:statement-result {:statements [stmt-2] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:agent agt-0} #{})))
          (is (= {:statement-result {:statements [stmt-3 stmt-2 stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:verb vrb-1} #{})))
          (is (= {:statement-result {:statements [stmt-2 stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:activity act-1} #{})))
          (is (= {:statement-result {:statements [stmt-3 stmt-2 stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs
                         auth-ident
                         {:activity act-1 :related_activities true}
                         #{})))))

      (testing "with both activities and agents"
        (is (= {:statement-result {:statements [stmt-1] :more ""}
                :attachments      []}
               (get-ss lrs auth-ident {:activity act-1 :agent agt-1} #{})))
        (is (= {:statement-result {:statements [stmt-3 stmt-1] :more ""}
                :attachments      []}
               (get-ss lrs
                       auth-ident
                       {:activity           act-1
                        :agent              agt-1
                        :related_activities true
                        :related_agents     true}
                       #{}))))

      (testing "querying with limits"
        (testing "(descending)"
          (is (= {:statement-result
                  {:statements [stmt-4 stmt-3]
                   :more       (format "%s/statements?limit=2&from=" pre)}
                  :attachments []}
                 (-> (get-ss lrs auth-ident {:limit 2} #{})
                     (update-in [:statement-result :more]
                                #(->> % (re-matches #"(.*from=).*") second)))))
          (is (= {:statement-result {:statements [stmt-2 stmt-1] :more ""}
                  :attachments      []}
                 (let [more (-> (get-ss lrs auth-ident {:limit 2} #{})
                                (get-in [:statement-result :more]))
                       from (->> more (re-matches #".*from=(.*)") second)]
                   (get-ss lrs auth-ident {:limit 2 :from from} #{}))))))
      (testing "(ascending)"
        (is (= {:statement-result
                {:statements [stmt-1 stmt-2]
                 :more       (format "%s/statements?limit=2&ascending=true&from="
                                     pre)}
                :attachments []}
               (-> (get-ss lrs auth-ident {:limit 2 :ascending true} #{})
                   (update-in [:statement-result :more]
                              #(->> % (re-matches #"(.*from=).*") second)))))
        (is (= {:statement-result {:statements [stmt-3 stmt-4] :more ""}
                :attachments      []}
               (let [more (-> (get-ss lrs auth-ident {:limit 2 :ascending true} #{})
                              (get-in [:statement-result :more]))
                     from (->> more (re-matches #".*from=(.*)") second)]
                 (get-ss lrs
                         auth-ident
                         {:limit 2 :ascending true :from from}
                         #{})))))

      (testing "(with actor)"
        (let [params {:limit 1
                      :agent {"name" "Sample Agent 1"
                              "mbox" "mailto:sample.agent@example.com"}
                      :related_agents true}]
          (is (= {:statement-result
                  {:statements [stmt-3]
                   :more       (str pre
                                    "/statements"
                                    "?" "limit=1"
                                    "&" "agent=%7B%22name%22%3A%22Sample+Agent+1%22%2C%22mbox%22%3A%22mailto%3Asample.agent%40example.com%22%7D"
                                    "&" "related_agents=true"
                                    "&" "from=")}
                  :attachments []}
                 (-> (get-ss lrs auth-ident params #{})
                     (update-in [:statement-result :more]
                                #(->> % (re-matches #"(.*from=).*") second)))))
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (let [more (-> (get-ss lrs auth-ident params #{})
                                (get-in [:statement-result :more]))
                       from (->> more (re-matches #".*from=(.*)") second)]
                   (get-ss lrs auth-ident (assoc params :from from) #{}))))))

      (testing "querying with attachments"
        (testing "(multiple)"
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      [(update-attachment-content stmt-4-attach)]}
                 (-> (get-ss lrs
                             auth-ident
                             {:activity act-4 :attachments true}
                             #{})
                     string-result-attachment-content))))

        (testing "(single)"
          (is (= {:statement    stmt-4
                  :attachments  [(update-attachment-content stmt-4-attach)]}
                 (-> (get-ss lrs
                             auth-ident
                             {:statementId (get stmt-4 "id") :attachments true}
                             #{})
                     string-result-attachment-content)))))

      (testing "agent query"
        (is (= {:person
                {"objectType" "Person"
                 "name"       ["Sample Agent 1"]
                 "mbox"       ["mailto:sample.agent@example.com"]}}
               (lrsp/-get-person lrs tc/ctx auth-ident {:agent agt-1}))))

      (testing "activity query"
        ;; Activity was updated between stmt-0 and stmt-1
        ;; Result should contain full definition.
        (is (= {:activity
                (get stmt-1 "object")}
               (lrsp/-get-activity lrs tc/ctx auth-ident {:activityId act-1}))))

      (testing "Extremely long IRIs"
        (is (= {:statement-ids [id-7]}
               (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-7] []))))
      (finally
        (component/stop sys')))))

(deftest reverse-activity-query-test
  (let [sys   (support/test-system)
        sys'  (component/start sys)
        lrs   (-> sys' :lrs)
        act-1 (get-in stmt-1 ["object" "id"])]
    (try
      (testing "activity query (reverse)"
        (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1] [])
        (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0] [])
        ;; Activity was updated between stmt-1 and stmt-0
        (is (= {:activity
                (get stmt-1 "object")}
               (lrsp/-get-activity lrs tc/ctx auth-ident {:activityId act-1}))))

      (finally
        (component/stop sys')))))

(deftest attachment-normalization-test
  (let [sys   (support/test-system)
        sys'  (component/start sys)
        lrs   (-> sys' :lrs)
        id-4  (get stmt-4 "id")
        id-5  (get stmt-5 "id")
        id-6  (get stmt-6 "id")
        act-4 (get-in stmt-4 ["object" "id"])]

    (testing "accepts normalized attachments"
      (is (= {:statement-ids [id-4
                              id-5
                              id-6]}
             (lrsp/-store-statements
              ;; stmt-5 references stmt-4-attach
              ;; stmt-6 references stmt-4-attach AND stmt-6-attach (twice)
              lrs tc/ctx auth-ident [stmt-4 stmt-5 stmt-6] [stmt-4-attach stmt-6-attach]))))

    (testing "returns normalized attachments"
      (testing "(multiple)"
        (testing "single attachment"
          (is (= {:statement-result {:statements [stmt-5 stmt-4] :more ""}
                  :attachments      [(update-attachment-content stmt-4-attach)]}
                 (-> (get-ss lrs
                             auth-ident
                             {:activity act-4
                              :attachments true}
                             #{})
                     string-result-attachment-content))))

        (testing "multiple attachments"
          (is (= {:statement-result {:statements [stmt-6 stmt-5 stmt-4] :more ""}
                  ;; Compare attachments as a set, their order is different on the
                  ;; postgres backend
                  :attachments #{(update-attachment-content stmt-6-attach)
                                 (update-attachment-content stmt-4-attach)}}
                 (-> (get-ss lrs
                             auth-ident
                             {:attachments true}
                             #{})
                     string-result-attachment-content
                     (update :attachments set))))))

      (testing "(single)"
        (is (= {:statement   stmt-6
                :attachments #{(update-attachment-content stmt-6-attach)
                               (update-attachment-content stmt-4-attach)}}
               (-> (get-ss lrs
                           auth-ident
                           {:statementId id-6 :attachments true}
                           #{})
                   string-result-attachment-content
                   (update :attachments set))))))
    (component/stop sys')))

(deftest context-agents-and-groups-query-test
  (binding [xs/*xapi-version* "2.0.0"] ;; for instrumentation
    (let [sys   (support/test-system)
          sys'  (component/start sys)
          lrs   (-> sys' :lrs)
          id-8  (get stmt-8 "id")
          agt-0 (-> stmt-8 (get "actor"))
          agt-ctx (-> stmt-8 (get-in ["context" "contextAgents" 0 "agent"]))
          grp-ctx (-> stmt-8 (get-in ["context" "contextGroups" 0 "group"])
                      (dissoc "name"))
          mem-ctx (-> stmt-8 (get-in ["context" "contextGroups" 0 "group" "member" 0])
                      (dissoc "name"))
          ctx     {:com.yetanalytics.lrs/version "2.0.0"}]
      (try
        (testing "statement insertions (2.0.0)"
          (is (= {:statement-ids [id-8]}
                 (lrsp/-store-statements lrs ctx auth-ident [stmt-8] []))))

        (testing "statement property queries (2.0.0)"
          (is (= {:statement-result {:statements [stmt-8] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:agent agt-0} #{})))
          (is (= {:statement-result {:statements [stmt-8] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:agent          agt-ctx
                                         :related_agents true} #{})))
          (is (= {:statement-result {:statements [stmt-8] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:agent          grp-ctx
                                         :related_agents true} #{})))
          (is (= {:statement-result {:statements [stmt-8] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident {:agent          mem-ctx
                                         :related_agents true} #{}))))
        (finally
          (component/stop sys'))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Ref Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stmt-1'
  {"id"     "00000000-0000-4000-0000-000000000001"
   "actor"  {"mbox"       "mailto:sample.0@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-2'
  {"id"     "00000000-0000-4000-0000-000000000002"
   "actor"  {"mbox"       "mailto:sample.1@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"objectType" "StatementRef"
             "id" "00000000-0000-4000-0000-000000000001"}})

(def stmt-3'
  {"id"     "00000000-0000-4000-0000-000000000003"
   "actor"  {"mbox"       "mailto:sample.2@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"objectType" "StatementRef"
             "id" "00000000-0000-4000-0000-000000000002"}})

(def stmt-4'
  {"id"     "00000000-0000-4000-0000-000000000004"
   "actor"  {"mbox"       "mailto:sample3@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "00000000-0000-4000-0000-000000000003"}})

(deftest test-statement-ref-fns
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')
        ts   "3000-01-01T01:00:00Z"]
    (testing "statement insertions"
      (is (= {:statement-ids ["00000000-0000-4000-0000-000000000001"
                              "00000000-0000-4000-0000-000000000002"
                              "00000000-0000-4000-0000-000000000003"]}
             (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1' stmt-2' stmt-3'] []))))

    (testing "statement queries"
      (is (= {:statement-result {:statements [stmt-3' stmt-2' stmt-1'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:agent {"mbox" "mailto:sample.0@example.com"
                              "objectType" "Agent"}}
                     #{})))
      (is (= {:statement-result {:statements [stmt-3' stmt-2' stmt-1'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:activity "http://www.example.com/tincan/activities/multipart"}
                     #{})))
      (is (= {:statement-result {:statements [stmt-3' stmt-2' stmt-1'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:verb "http://adlnet.gov/expapi/verbs/answered"}
                     #{})))
      (is (= {:statement-result {:statements [] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:since ts}
                     #{})))
      (is (= {:statement-result {:statements [stmt-1' stmt-2' stmt-3'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:until ts :ascending true}
                     #{})))
      (is (= {:statement-result {:statements [stmt-3']}
              :attachments      []}
             (-> (get-ss lrs
                         auth-ident
                         {:activity "http://www.example.com/tincan/activities/multipart"
                          :limit    1}
                         #{})
                 (update :statement-result dissoc :more)))))

    (testing "don't return voided statement refs"
      (is (= {:statement-ids ["00000000-0000-4000-0000-000000000004"]}
             (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-4'] [])))
      ;; stmt-4' is returned because it targets stmt-3', whose voided status
      ;; does not matter. On the other hand, stmt-3' itself is not returned
      ;; because it was voided.
      (is (= {:statement-result {:statements [stmt-4'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:agent {"mbox" "mailto:sample.2@example.com"}}
                     #{})))
      ;; stmt-2' is returned directly (not as a stmt ref target)
      ;; since it is not voided.
      (is (= {:statement-result {:statements [stmt-4' stmt-2'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:agent {"mbox" "mailto:sample.1@example.com"}}
                     #{})))
      ;; stmt-1' is returned directly (not as a stmt ref target).
      ;; stmt-2' is returned since it refers to stmt-1' and is not voided
      (is (= {:statement-result {:statements [stmt-4' stmt-2' stmt-1'] :more ""}
              :attachments      []}
             (get-ss lrs
                     auth-ident
                     {:agent {"mbox" "mailto:sample.0@example.com"}}
                     #{}))))

    (component/stop sys')))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Read Scope Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Make it look like 3-legged OAuth
;; See: https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#example-7
(def auth-ident-oauth
  ;; Make user identical to that of `auth-ident` so
  ;; we can test overlapping authority agents
  {:agent {"objectType" "Group"
           "member"     [;; OAuth consumer
                         {"account" {"homePage" "http://example.com/xAPI/OAuth/Token"
                                     "name"     "oauth_consumer_x75db"}}
                         ;; OAuth user - identical to `auth-ident`
                         {"account" {"homePage" "http://example.org"
                                     "name"     "12341234-0000-4000-1234-123412341234"}}]}
   :scopes #{:scope/all}})

;; It is a common scenario for two OAuth authorities to share the
;; same client ID but different user account IDs
(def auth-ident-oauth*
  (assoc-in auth-ident-oauth
            [:agent "member" 1 "account" "name"]
            "55555555-0000-4000-5555-555555555555"))

(defn- get-ss-authority
  [lrs ctx auth-ident params ltags]
  (-> (lrsp/-get-statements lrs ctx auth-ident params ltags)
      :statement
      (get "authority")
      (update "member" set)))

(deftest test-statement-read-scopes
  (let [sys    (support/test-system)
        sys'   (component/start sys)
        lrs    (:lrs sys')
        id-0   (get stmt-0 "id")
        id-1   (get stmt-1 "id")
        id-2   (get stmt-2 "id")
        id-3   (get stmt-3 "id")
        id-4   (get stmt-4 "id")
        agt-0  (get stmt-0 "actor")
        agt-1  (get stmt-1 "actor")
        agt-2  (get stmt-2 "actor")
        agt-3  (get stmt-3 "actor")
        agt-4  (get stmt-4 "actor")
        ans-v  (get-in stmt-1 ["verb" "id"])
        mp-obj (get-in stmt-1 ["object" "id"])
        vrb-4  (get-in stmt-4 ["verb" "id"])
        obj-4  (get-in stmt-4 ["object" "id"])]
    (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0] [])
    (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1] [])
    (lrsp/-store-statements lrs tc/ctx auth-ident-oauth [stmt-2] [])
    (lrsp/-store-statements lrs tc/ctx auth-ident-oauth [stmt-3] [])
    (lrsp/-store-statements lrs tc/ctx auth-ident-oauth* [stmt-4] [stmt-4-attach])
    (testing "statements/read"
      (let [auth-ident-1 (assoc auth-ident
                                :scopes #{:scope/statements.read})
            auth-ident-2 (assoc auth-ident-oauth
                                :scopes #{:scope/statements.read})
            auth-ident-3 (assoc auth-ident-oauth*
                                :scopes #{:scope/statements.read})]
        (testing "- statement ID query"
          (is (= {:statement stmt-0}
                 (get-ss lrs auth-ident-1 {:voidedStatementId id-0} #{})
                 (get-ss lrs auth-ident-2 {:voidedStatementId id-0} #{})
                 (get-ss lrs auth-ident-3 {:voidedStatementId id-0} #{})))
          (is (= {:statement stmt-1}
                 (get-ss lrs auth-ident-1 {:statementId id-1} #{})
                 (get-ss lrs auth-ident-2 {:statementId id-1} #{})
                 (get-ss lrs auth-ident-3 {:statementId id-1} #{})))
          (is (= {:statement stmt-2}
                 (get-ss lrs auth-ident-1 {:statementId id-2} #{})
                 (get-ss lrs auth-ident-2 {:statementId id-2} #{})
                 (get-ss lrs auth-ident-3 {:statementId id-2} #{})))
          (is (= {:statement stmt-3}
                 (get-ss lrs auth-ident-1 {:statementId id-3} #{})
                 (get-ss lrs auth-ident-2 {:statementId id-3} #{})
                 (get-ss lrs auth-ident-3 {:statementId id-3} #{})))
          (is (= {:statement stmt-4}
                 (get-ss lrs auth-ident-1 {:statementId id-4} #{})
                 (get-ss lrs auth-ident-2 {:statementId id-4} #{})
                 (get-ss lrs auth-ident-3 {:statementId id-4} #{})))
          (is (= (-> auth-ident :agent (update "member" set))
                 (get-ss-authority lrs tc/ctx auth-ident-1 {:voidedStatementId id-0} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-2 {:voidedStatementId id-0} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-3 {:voidedStatementId id-0} #{})))
          (is (= (-> auth-ident :agent (update "member" set))
                 (get-ss-authority lrs tc/ctx auth-ident-1 {:statementId id-1} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-2 {:statementId id-1} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-3 {:statementId id-1} #{})))
          (is (= (-> auth-ident-oauth :agent (update "member" set))
                 (get-ss-authority lrs tc/ctx auth-ident-1 {:statementId id-2} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-2 {:statementId id-2} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-3 {:statementId id-2} #{})))
          (is (= (-> auth-ident-oauth :agent (update "member" set))
                 (get-ss-authority lrs tc/ctx auth-ident-1 {:statementId id-3} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-2 {:statementId id-3} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-3 {:statementId id-3} #{})))
          (is (= (-> auth-ident-oauth* :agent (update "member" set))
                 (get-ss-authority lrs tc/ctx auth-ident-1 {:statementId id-4} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-2 {:statementId id-4} #{})
                 (get-ss-authority lrs tc/ctx auth-ident-3 {:statementId id-4} #{}))))
        (testing "- statement property query"
          ;; stmt-0 is not returned since it was voided by stmt-2
          (is (= {:statement-result {:statements [stmt-2] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-0} #{})
                 (get-ss lrs auth-ident-2 {:agent agt-0} #{})
                 (get-ss lrs auth-ident-3 {:agent agt-0} #{})))
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-1} #{})
                 (get-ss lrs auth-ident-2 {:agent agt-1} #{})
                 (get-ss lrs auth-ident-3 {:agent agt-1} #{})))
          (is (= {:statement-result {:statements [stmt-2] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-2} #{})
                 (get-ss lrs auth-ident-2 {:agent agt-2} #{})
                 (get-ss lrs auth-ident-3 {:agent agt-2} #{})))
          (is (= {:statement-result {:statements [stmt-3] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-3} #{})
                 (get-ss lrs auth-ident-2 {:agent agt-3} #{})
                 (get-ss lrs auth-ident-3 {:agent agt-3} #{})))
          ;; Querying on verb
          ;; stmt-2 references voided stmt-0 with ans-v
          (is (= {:statement-result {:statements [stmt-3 stmt-2 stmt-1]
                                     :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:verb ans-v} #{})
                 (get-ss lrs auth-ident-2 {:verb ans-v} #{})
                 (get-ss lrs auth-ident-3 {:verb ans-v} #{})))
          ;; Querying on activity
          ;; stmt-2 references voided stmt-0 with mp-obj
          (is (= {:statement-result {:statements [stmt-2 stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:activity mp-obj} #{})
                 (get-ss lrs auth-ident-2 {:activity mp-obj} #{})
                 (get-ss lrs auth-ident-3 {:activity mp-obj} #{})))
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-1 :activity mp-obj} #{})
                 (get-ss lrs auth-ident-2 {:agent agt-1 :activity mp-obj} #{})
                 (get-ss lrs auth-ident-3 {:agent agt-1 :activity mp-obj} #{})))
          ;; Querying with attachments
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      [(update-attachment-content stmt-4-attach)]}
                 (string-result-attachment-content
                  (get-ss lrs auth-ident-1 {:agent agt-4 :attachments true} #{}))
                 (string-result-attachment-content
                  (get-ss lrs auth-ident-2 {:agent agt-4 :attachments true} #{}))
                 (string-result-attachment-content
                  (get-ss lrs auth-ident-3 {:agent agt-4 :attachments true} #{})))))))
    (testing "statements/read/mine"
      (let [auth-ident-1 (assoc auth-ident
                                :scopes #{:scope/statements.read.mine})
            auth-ident-2 (assoc auth-ident-oauth
                                :scopes #{:scope/statements.read.mine})
            auth-ident-3 (assoc auth-ident-oauth*
                                :scopes #{:scope/statements.read.mine})]
        (testing "- statement ID query"
          (is (= {:statement stmt-0}
                 (get-ss lrs auth-ident-1 {:voidedStatementId id-0} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-2 {:voidedStatementId id-0} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-3 {:voidedStatementId id-0} #{})))
          (is (= {:statement stmt-1}
                 (get-ss lrs auth-ident-1 {:statementId id-1} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-2 {:statementId id-1} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-3 {:statementId id-1} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-1 {:statementId id-2} #{})))
          (is (= {:statement stmt-2}
                 (get-ss lrs auth-ident-2 {:statementId id-2} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-3 {:statementId id-2} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-1 {:statementId id-4} #{})))
          (is (= {:statement nil}
                 (get-ss lrs auth-ident-2 {:statementId id-4} #{})))
          (is (= {:statement stmt-4}
                 (get-ss lrs auth-ident-3 {:statementId id-4} #{}))))
        (testing "- statement property query"
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {} #{})))
          (is (= {:statement-result {:statements [stmt-3 stmt-2] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {} #{})))
          (is (= {:statement-result {:statements [stmt-2 stmt-3] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:ascending true} #{})))
          (is (= {:statement-result {:statements [stmt-3]
                                     :more "/xapi/statements?limit=1&from="}
                  :attachments      []}
                 (-> (get-ss lrs auth-ident-2 {:limit 1} #{})
                     (update-in [:statement-result :more] cstr/replace #"from=.*" "from="))))
          (is (= {:statement-result {:statements [stmt-2]
                                     :more "/xapi/statements?ascending=true&limit=1&from="}
                  :attachments      []}
                 (-> (get-ss lrs auth-ident-2 {:ascending true :limit 1} #{})
                     (update-in [:statement-result :more] cstr/replace #"from=.*" "from="))))
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {} #{})))
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      [(update-attachment-content stmt-4-attach)]}
                 (string-result-attachment-content
                  (get-ss lrs auth-ident-3 {:attachments true} #{}))))
          ;; Query on agents
          ;; stmt-2 not returned since it's outside the scope of auth-ident-1
          ;; stmt-0 not returned since it was voided
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-0} #{})))
          ;; stmt-2 not returned since its target statement (stmt-0) is
          ;; outside the scope of auth-ident-2
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:agent agt-0} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:agent agt-0} #{})))
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-1} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:agent agt-1} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:agent agt-1} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-2} #{})))
          (is (= {:statement-result {:statements [stmt-2] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:agent agt-2} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:agent agt-2} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-3} #{})))
          (is (= {:statement-result {:statements [stmt-3] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:agent agt-3} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:agent agt-3} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:agent agt-4} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:agent agt-4} #{})))
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:agent agt-4} #{})))
          ;; Querying on verb
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:verb ans-v} #{})))
          (is (= {:statement-result {:statements [stmt-3] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:verb ans-v} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:verb ans-v} #{})))
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:verb vrb-4} #{})))
          ;; Querying on activity
          (is (= {:statement-result {:statements [stmt-1] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-1 {:activity mp-obj} #{})
                 (get-ss lrs auth-ident-1 {:activity mp-obj :agent agt-1} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-2 {:activity mp-obj} #{})))
          (is (= {:statement-result {:statements [] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:activity mp-obj} #{})))
          (is (= {:statement-result {:statements [stmt-4] :more ""}
                  :attachments      []}
                 (get-ss lrs auth-ident-3 {:activity obj-4} #{}))))))
    (component/stop sys')))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement DATASIM Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Taken from lrs and third lib tests
;; We reuse bench resources for tests here.



(defn trunc [n d]
  (let [pow (Math/pow 10 d)]
    (-> n
        (* pow)
        (Math/floor)
        (/ pow))))

(def test-statements
  (->> (support/bench-statements 50)
       (walk/prewalk
        (fn [v]
          (if (and (number? v)
                   (not= v (Math/floor v)))
            (let [before (count (str (int (Math/floor v)) ))]
              (trunc v (- 15 before)))
            v)))))

(deftest datasim-tests
  (let [sys     (support/test-system)
        sys'    (component/start sys)
        lrs     (:lrs sys')
        get-ss' (fn [params]
                  (-> (lrsp/-get-statements lrs tc/ctx auth-ident params #{"en-US"})
                      :statement-result
                      :statements))]
    (lrsp/-store-statements lrs tc/ctx auth-ident test-statements [])
    (testing "descending query"
      (let [query-res (get-ss' {:limit 50})]
        (is (= 50
               (count query-res)))
        (is (= (-> test-statements
                   last
                   (dissoc "authority" "version" "stored"))
               (-> query-res
                   first
                   (dissoc "authority" "version" "stored"))))
        (is (= (->> query-res
                    (map #(get % "stored")))
               (->> query-res
                    (map #(get % "stored"))
                    (sort (comp #(* % -1) compare)))))))

    (testing "ascending query"
      (let [query-res (get-ss' {:ascending true :limit 50})]
        (is (= 50
               (count query-res)))
        (is (= (->> query-res
                    (map #(get % "stored")))
               (->> query-res
                    (map #(get % "stored"))
                    (sort compare))))))

    ;; Important difference from third/lrs: `stored` timestamps are not
    ;; monotonic in lrsql, unlike in those other libs. Because of this,
    ;; query result counts will vary depending on the exact timestamp values.
    (testing "since + until:"
      (let [query-res  (-> (lrsp/-get-statements lrs tc/ctx auth-ident {:limit 50} #{})
                           :statement-result
                           :statements)
            ;; since
            fst-stored (-> query-res last (get "stored"))
            since-fst  (take-while #(-> % (get "stored") (not= fst-stored))
                                   query-res)
            snd-stored (-> since-fst last (get "stored"))
            since-snd  (take-while #(-> % (get "stored") (not= snd-stored))
                                   since-fst)
            ;; until
            lst-stored (-> query-res first (get "stored"))
            until-lst  (drop-while #(-> % (get "stored") (= lst-stored))
                                   query-res)
            pen-stored (-> until-lst first (get "stored"))]
        (testing "since only"
          (is (= (count since-fst)
                 (count (get-ss' {:since fst-stored
                                  :limit 50}))))
          (is (= (count since-snd)
                 (count (get-ss' {:since snd-stored
                                  :limit 50}))))
          (is (= (count since-fst)
                 (count (get-ss' {:ascending true
                                  :since     fst-stored
                                  :limit     50}))))
          (is (= (count since-snd)
                 (count (get-ss' {:ascending true
                                  :since     snd-stored
                                  :limit     50})))))

        (testing "until only"
          (is (= 50
                 (count (get-ss' {:until lst-stored
                                  :limit 50}))))
          (is (= (count until-lst)
                 (count (get-ss' {:until pen-stored
                                  :limit 50}))))
          (is (= 50
                 (count (get-ss' {:ascending true
                                  :until     lst-stored
                                  :limit     50}))))
          (is (= (count until-lst)
                 (count (get-ss' {:ascending true
                                  :until     pen-stored
                                  :limit     50})))))

        (testing "both"
          (is (= (count since-fst)
                 (count (get-ss' {:since fst-stored
                                  :until lst-stored
                                  :limit 50}))))
          (is (= (count since-fst)
                 (count (get-ss' {:ascending true
                                  :since     fst-stored
                                  :until     lst-stored
                                  :limit     50})))))))
    (testing "UUID params ignore case"
      (let [id  (-> test-statements first (get "id"))
            reg (-> test-statements first (get-in ["context" "registration"]))]
        (is (:statement (lrsp/-get-statements
                         lrs
                         tc/ctx
                         auth-ident
                         {:statementId (cstr/upper-case id)}
                         #{})))
        (is (not-empty (get-ss' {:registration (cstr/upper-case reg)})))))

    (testing "UUID keys ignore case"
      (let [id "00000000-0000-4000-8000-abcdefabcdef"
            stmt (-> test-statements
                     first
                     (assoc "id" (cstr/upper-case id))
                     (assoc-in ["context" "registration"] (cstr/upper-case id)))
            _ (lrsp/-store-statements lrs tc/ctx auth-ident [stmt] [])]
        (is (:statement (lrsp/-get-statements
                         lrs
                         tc/ctx
                         auth-ident
                         {:statementId id}
                         #{})))
        (is (not-empty (get-ss' {:verb (get-in stmt ["verb" "id"])})))
        (is (not-empty (get-ss' {:registration id})))
        ;; Original case is preserved
        (is (= (cstr/upper-case id)
               (-> (lrsp/-get-statements
                    lrs
                    tc/ctx
                    auth-ident
                    {:statementId id}
                    #{})
                   (get-in [:statement "id"]))))))
    (component/stop sys')))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Reaction Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- remove-id
  [statement]
  (dissoc statement "id"))

(deftest reaction-test
  (let [sys           (support/test-system)
        sys'          (component/start sys)
        {:keys [lrs]} sys']
    (try
      (testing "Processes simple reaction"
        (let [;; Add a good reaction
              {reaction-id
               :result} (adp/-create-reaction
                          lrs "reaction-0" tc/simple-reaction-ruleset true)
              ;; Add a bad reaction
              bad-ruleset
              (assoc
               tc/simple-reaction-ruleset
               :template
               tc/invalid-template-invalid-path)
              {bad-reaction-id
               :result} (adp/-create-reaction
                          lrs "reaction-bad" bad-ruleset true)]
          ;; Add statements
          (doseq [s [tc/reaction-stmt-a
                     tc/reaction-stmt-b]]
            (Thread/sleep 100)
            (lrsp/-store-statements lrs tc/ctx tc/auth-ident [s] []))
          ;; Wait a little bit for the reactor
          (Thread/sleep 300)
          (testing "New statement added"
            (is (= {:statement-result
                    {:statements
                     [tc/reaction-stmt-result
                      (remove-id tc/reaction-stmt-b)
                      (remove-id tc/reaction-stmt-a)]
                     :more ""}
                    :attachments []}
                   (-> (lrsp/-get-statements
                        lrs
                        tc/ctx
                        tc/auth-ident
                        {}
                        [])
                       ;; Remove LRS fields
                       (update-in
                        [:statement-result :statements]
                        #(mapv (comp
                                remove-id
                                remove-props)
                               %))))))
          (testing "Bad ruleset error is retrievable"
            (is (= [{:id      reaction-id
                     :title   "reaction-0"
                     :ruleset tc/simple-reaction-ruleset
                     :active  true
                     :error   nil}
                    {:id      bad-reaction-id
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state-id-params
  {:stateId    "some-id"
   :activityId "https://example.org/activity-type"
   :agent      {"mbox" "mailto:example@example.org"}})

(def state-id-params-2
  {:stateId "some-other-id"
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

;; Test SQL-124

(def bad-doc-params
  {:profileId "https://example.org/some-profile"
   :agent     {"mbox" "mailto:badguy@evil.org"
               "name" "Bad Guy"}})

(def bad-doc
  {:contents (.getBytes "I'm a bad guy")})

(def bad-doc-params-2
  {:profileId "https://example.org/some-profile"
   :agent     {"mbox" "mailto:jackson5@example.org"
               "name" "The Jackson 5"}})

(def bad-doc-a
  {:contents     (.getBytes "{\"ABC\": 1}")
   :content-type "application/json"})

(def bad-doc-b
  {:contents     (.getBytes "{\"123\": 2}")
   :content-type "application/json"})

(defn- get-doc
  "Same as lrsp/-get-documents except automatically formats the result."
  [lrs ctx auth-ident params]
  (-> (lrsp/-get-document lrs ctx auth-ident params)
      (update :document dissoc :updated)
      (update-in [:document :contents] u/bytes->str)))

(deftest test-document-fns
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')]
    (testing "document insertion"
      (support/seq-is
       {}
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params
                           state-doc-1
                           true)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params
                           state-doc-2
                           true)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params-2
                           state-doc-1
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params
                           state-doc-2
                           true)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params-2
                           state-doc-1
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params-2
                           state-doc-2
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           agent-prof-id-params
                           agent-prof-doc
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           activity-prof-id-params
                           activity-prof-doc
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           bad-doc-params
                           bad-doc
                           false)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           bad-doc-params-2
                           bad-doc-a
                           true)
       (lrsp/-set-document lrs
                           tc/ctx
                           auth-ident
                           bad-doc-params-2
                           bad-doc-b
                           true)))

    (testing "document query"
      (is (= {:document
              {:contents       "{\"foo\":10,\"bar\":2}"
               :content-length 18
               :content-type   "application/json"
               :id             "some-id"}}
             (get-doc lrs tc/ctx auth-ident state-id-params)))
      (is (= {:document
              {:contents       "{\"foo\":10}"
               :content-length 10
               :content-type   "application/json"
               :id             "some-other-id"}}
             (get-doc lrs tc/ctx auth-ident state-id-params-2)))
      (is (= {:document
              {:contents       "Example Document"
               :content-length 16
               :content-type   "text/plain"
               :id             "https://example.org/some-profile"}}
             (get-doc lrs tc/ctx auth-ident agent-prof-id-params)))
      (is (= {:document
              {:contents       "Example Document 2"
               :content-length 18
               :content-type   "text/plain"
               :id             "https://example.org/some-profile"}}
             (get-doc lrs tc/ctx auth-ident activity-prof-id-params)))
      (is (= {:document
              {:contents       "I'm a bad guy"
               :content-length 13
               :content-type   "application/octet-stream"
               :id             "https://example.org/some-profile"}}
             (get-doc lrs tc/ctx auth-ident bad-doc-params)))
      (is (= {:document
              {:contents       "{\"ABC\":1,\"123\":2}"
               :content-length 17
               :content-type   "application/json"
               :id             "https://example.org/some-profile"}}
             (get-doc lrs tc/ctx auth-ident bad-doc-params-2))))

    (testing "document ID query"
      (is (= {:document-ids ["some-id" "some-other-id"]}
             (lrsp/-get-document-ids
              lrs
              tc/ctx
              auth-ident
              (dissoc state-id-params :stateId))))
      (is (= {:document-ids ["https://example.org/some-profile"]}
             (lrsp/-get-document-ids
              lrs
              tc/ctx
              auth-ident
              (dissoc agent-prof-id-params :profileId))))
      (is (= {:document-ids ["https://example.org/some-profile"]}
             (lrsp/-get-document-ids
              lrs
              tc/ctx
              auth-ident
              (dissoc activity-prof-id-params :profileId)))))

    (testing "document deletion"
      (support/seq-is
       {}
       (lrsp/-delete-documents lrs
                               tc/ctx
                               auth-ident
                               (dissoc state-id-params :stateId))
       (lrsp/-delete-document lrs
                              tc/ctx
                              auth-ident
                              agent-prof-id-params)
       (lrsp/-delete-document lrs
                              tc/ctx
                              auth-ident
                              activity-prof-id-params))
      (support/seq-is
       {:document nil}
       (lrsp/-get-document lrs
                           tc/ctx
                           auth-ident
                           state-id-params)
       (lrsp/-get-document lrs
                           tc/ctx
                           auth-ident
                           agent-prof-id-params)
       (lrsp/-get-document lrs
                           tc/ctx
                           auth-ident
                           activity-prof-id-params)))

    (component/stop sys')))
