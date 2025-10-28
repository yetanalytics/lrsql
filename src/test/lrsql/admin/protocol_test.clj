(ns lrsql.admin.protocol-test
  "Test the protocol fns of `AdminAccountManager`, `APIKeyManager`, `AdminStatusProvider` directly."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.data.csv              :as csv]
            [next.jdbc                     :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.squuid       :as squuid]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.admin.protocol :as adp]
            [lrsql.lrs-test       :as lrst]
            [lrsql.backend.protocol :as bp]
            [lrsql.test-support   :as support]
            [lrsql.util           :as u]
            [lrsql.test-constants :as tc]
            [lrsql.util.actor     :as ua]
            [lrsql.util.admin     :as uadm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def test-username "DonaldChamberlin123") ; co-inventor of SQL

(def test-username-2 "MichaelBJones456") ; co-inventor of JWTs

(def test-password "iLoveSqlS0!")

;; Some statement data for status test
(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

(def stmt-0
  {"id"      "00000000-0000-4000-8000-000000000000"
   "actor"   {"mbox"       "mailto:sample.foo@example.com"
              "objectType" "Agent"}
   "verb"    {"id"      "http://adlnet.gov/expapi/verbs/answered"
              "display" {"en-US" "answered"
                         "zh-CN" "回答了"}}
   "object"  {"id" "http://www.example.com/tincan/activities/multipart"}
   "context" {"platform" "example"}})

;; A second statement with the same actor but a different example.
(def stmt-1
  {"id"      "00000000-0000-4000-8000-000000000001"
   "actor"   {"mbox"       "mailto:sample.foo@example.com"
              "objectType" "Agent"}
   "verb"    {"id"      "http://adlnet.gov/expapi/verbs/answered"
              "display" {"en-US" "answered"
                         "zh-CN" "回答了"}}
   "object"  {"id" "http://www.example.com/tincan/activities/multipart"}
   "context" {"platform" "another_example"}})

;; A third statement with a different actor, referring to stmt-0
(def stmt-2
  {"id"     "00000000-0000-4000-8000-000000000002"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homePage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/asked"
             "display" {"en-US" "asked"}}
   "object" {"objectType" "StatementRef"
             "id"         "00000000-0000-4000-8000-000000000000"}})

;; A fourth statement (with a third actor) containing a substatement (by a fourth actor
(def stmt-3
  {"id"     "00000000-0000-4000-8000-000000000003"
   "actor"  {"mbox"        "mailto:sample.bar@example.com"
              "objectType" "Agent"}
   "verb"   {"id"          "http://adlnet.gov/expapi/verbs/asked"
             "display"     {"en-US" "asked"}}
   "object" {"objectType"  "SubStatement"
             "actor"       {"mbox"       "mailto:sample.baz@example.com"
                            "objectType" "Agent"}
             "verb"        {"id"      "http://adlnet.gov/expapi/verbs/answered"
                            "display" {"en-US" "answered"}}
             "object"      {"id" "http://www.example.com/tincan/activities/multipart"}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest admin-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')
        ds (-> lrs :connection :conn-pool)]
    (try
      (testing "Admin account insertion"
        (is (-> (adp/-create-account lrs test-username test-password)
                :result
                uuid?))
        (is (-> (adp/-create-account lrs test-username test-password)
                :result
                (= :lrsql.admin/existing-account-error)))
        (is (-> (adp/-create-account lrs test-username-2 test-password)
                :result
                uuid?)))
      (testing "Admin account get"
        (let [accounts (adp/-get-accounts lrs)]
          (is (vector? accounts))
          (is (some (fn [acct] (= (:username acct) test-username)) accounts))))
      (testing "Admin account authentication"
        (is (-> (adp/-authenticate-account lrs test-username test-password)
                :result
                uuid?))
        (is (-> (adp/-authenticate-account lrs test-username "badPass")
                :result
                (= :lrsql.admin/invalid-password-error)))
        (is (-> (adp/-authenticate-account lrs "foo" "bar")
                :result
                (= :lrsql.admin/missing-account-error))))
      (testing "Admin account existence check"
        (let [account-id (-> (adp/-authenticate-account lrs
                                                        test-username
                                                        test-password)
                             :result)]
          (is (adp/-existing-account? lrs account-id)))
        (let [bad-account-id #uuid "00000000-0000-4000-8000-000000000000"]
          (is (not (adp/-existing-account? lrs bad-account-id)))))
      (testing "Admin JWTs"
        (let [exp    2
              leeway 1
              jwt    "Foo"]
          (testing "- are unblocked by default"
            (is (false?
                 (adp/-jwt-blocked? lrs jwt))))
          (testing "- can be blocked"
            (is (= jwt
                   (:result (adp/-block-jwt lrs jwt exp))))
            (is (true?
                 (adp/-jwt-blocked? lrs jwt))))
          (testing "- cannot insert duplicates into blocklist"
            (is (some? (:error (adp/-block-jwt lrs jwt exp)))))
          (testing "- cannot be purged from blocklist if not expired"
            (is (= nil
                   (adp/-purge-blocklist lrs leeway)))
            (is (true?
                 (adp/-jwt-blocked? lrs jwt))))
          (testing "- not counted as expired in blocklist due to leeway"
            (Thread/sleep 2000)
            (is (= nil
                   (adp/-purge-blocklist lrs leeway)))
            (is (true?
                 (adp/-jwt-blocked? lrs jwt))))
          (testing "- can be purged from blocklist when expired"
            (Thread/sleep 2000)
            (is (= nil
                   (adp/-purge-blocklist lrs leeway)))
            (is (false?
                 (adp/-jwt-blocked? lrs jwt))))))
      (testing "Admin one-time JWTs"
        (let [{:keys [jwt exp oti]}
              (uadm/one-time-jwt {} "MySecret" 100)]
          (testing "- can be added"
            (is (adp/-create-one-time-jwt lrs jwt exp oti))
            (is (false? (adp/-jwt-blocked? lrs jwt))))
          (testing "- can be blocked"
            (is (adp/-block-one-time-jwt lrs jwt oti))
            (is (true? (adp/-jwt-blocked? lrs jwt))))))
      (testing "Admin password update"
        (let [account-id   (-> (adp/-authenticate-account lrs
                                                          test-username
                                                          test-password)
                               :result)
              new-password "iLoveN0Sql!"]
          (testing "Valid update"
            (adp/-update-admin-password lrs account-id test-password new-password)
            (is (-> (adp/-authenticate-account lrs
                                               test-username
                                               new-password)
                    :result
                    (= account-id))))
          (testing "Invalid update"
            (is (-> (adp/-update-admin-password
                     lrs account-id "iLoveM0ngoDB!" test-password)
                    :result
                    (= :lrsql.admin/invalid-password-error))))
          ;; Change it back for subsequent tests
          (adp/-update-admin-password
           lrs account-id new-password test-password)))
      (testing "Admin account deletion"
        (let [account-id   (-> (adp/-authenticate-account lrs
                                                          test-username
                                                          test-password)
                               :result)
              account-id-2 (-> (adp/-authenticate-account lrs
                                                          test-username-2
                                                          test-password)
                               :result)]
          (testing "When OIDC is off"
            (let [oidc-enabled? false]
              (testing "Succeeds if there is more than one account"
                (adp/-delete-account lrs account-id oidc-enabled?)
                (adp/-delete-account lrs account-id-2 oidc-enabled?)
                (is (-> (adp/-authenticate-account lrs test-username test-password)
                        :result
                        (= :lrsql.admin/missing-account-error)))
                (is (-> (adp/-authenticate-account lrs test-username-2 test-password)
                        :result
                        (= :lrsql.admin/missing-account-error))))
              (testing "Fails if there is only one account"
                (let [default-account-id (:result
                                          (adp/-authenticate-account
                                           lrs "username" "password"))]
                  (is (-> (adp/-delete-account
                           lrs default-account-id oidc-enabled?)
                          :result
                          (= :lrsql.admin/sole-admin-delete-error)))))))
          (testing "When OIDC is on"
            (let [oidc-enabled? true]
              (testing "Succeeds if there is only one account"
                (let [default-account-id (:result
                                          (adp/-authenticate-account
                                           lrs "username" "password"))]
                  (is (-> (adp/-delete-account
                           lrs default-account-id oidc-enabled?)
                          :result
                          (= default-account-id)))))))))
      (testing "Admin account OIDC bootstrap"
        (let [username    "oidcsub"
              oidc-issuer "https://example.com/realm"
              bad-issuer  "https://impostor.com/realm"]
          ;; Creates if does not exist
          (is (-> (adp/-ensure-account-oidc lrs username oidc-issuer)
                  :result
                  uuid?))
          ;; Idempotent
          (is (= (adp/-ensure-account-oidc lrs username oidc-issuer)
                 (adp/-ensure-account-oidc lrs username oidc-issuer)))
          ;; OIDC issuer must match
          (is (-> (adp/-ensure-account-oidc lrs username bad-issuer)
                  :result
                  (= :lrsql.admin/oidc-issuer-mismatch-error)))))

      (testing "delete actor"
        (testing "delete actor: delete actor"
          (let [actor (stmt-1 "actor")]
            (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1] [])
            (adp/-delete-actor lrs {:actor-ifi (ua/actor->ifi actor)})
            (is (= (lrsp/-get-person lrs tc/ctx auth-ident {:agent actor})
                   {:person {"objectType" "Person"}}))))
        (let [arb-query #(jdbc/execute! ds %)]
          (testing "delete-actor: delete statements related to actor"
            (let [stmts [stmt-0 stmt-1 stmt-2 stmt-3]
                  ifis (->> (conj stmts (stmt-3 "object"))
                            (map #(ua/actor->ifi (% "actor"))))
                  get-actor-ss-count (fn [ifi]
                                       (-> (lrsp/-get-statements lrs tc/ctx auth-ident {:actor-ifi ifi} [])
                                           (get-in [:statement-result :statements])
                                           count))
                  get-stmt-#s (fn []
                                (reduce (fn [m actor-ifi]
                                          (assoc m actor-ifi (get-actor-ss-count actor-ifi)))
                                        {} ifis))]

              (lrsp/-store-statements lrs tc/ctx auth-ident stmts [])
              (is (every? pos-int? (vals (get-stmt-#s))))
              (doseq [ifi ifis]
                (adp/-delete-actor lrs {:actor-ifi ifi}))
              (is (every? zero? (vals (get-stmt-#s))))))
          (testing "delete-actor: delete statements related to deleted statements"
            (let [stmt->ifi #(ua/actor->ifi (% "actor"))
                  count-of-actor (fn [actor-ifi] (-> (lrsp/-get-statements lrs tc/ctx auth-ident {:actor-ifi actor-ifi} []) :statement-result :statements count))
                  child-ifi (stmt->ifi (stmt-3 "object"))
                  parent-ifi (stmt->ifi stmt-3)]
              (testing "delete-actor correctly deletes statements that are parent to actor (sub)statements"
                (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-3] [])
                (is (= 1 (count-of-actor parent-ifi)))
                (adp/-delete-actor lrs {:actor-ifi child-ifi})
                (is (zero? (count-of-actor parent-ifi))) ;
                (adp/-delete-actor lrs {:actor-ifi parent-ifi}))
              (testing "delete-actor correctly deletes substatements that are child to actor statements"
                (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-3] [])
                (is (= 1 (count-of-actor child-ifi)))
                (adp/-delete-actor lrs {:actor-ifi parent-ifi})
                (is (zero? (count-of-actor child-ifi)))
                (adp/-delete-actor lrs {:actor-ifi child-ifi}))
              (testing "for StatementRefs, delete-actor deletes statement->actor relationships but leaves statements by another actor untouched"
                (let [[ifi-0 ifi-2] (mapv stmt->ifi [stmt-0 stmt-2])]
                  (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0 stmt-2] [])
                  (is (= [2 2] (mapv count-of-actor [ifi-0 ifi-2])))
                  (adp/-delete-actor lrs {:actor-ifi ifi-0})
                  (is (= [1 1] (mapv count-of-actor [ifi-0 ifi-2])))
                  (adp/-delete-actor lrs {:actor-ifi ifi-2})
                  (is (= [0 0] (mapv count-of-actor [ifi-0 ifi-2])))))))

          (testing "delete-actor: delete state_document of deleted actor"
            (let [ifi (ua/actor->ifi (:agent lrst/state-id-params))]
              (lrsp/-set-document lrs tc/ctx auth-ident lrst/state-id-params lrst/state-doc-1 true)
              (adp/-delete-actor lrs {:actor-ifi ifi})
              (is (empty? (arb-query ["select * from state_document where agent_ifi  = ?" ifi])))))

          (testing "delete-actor: delete agent profile document of deleted actor"
            (let [{:keys [agent profileId]}  lrst/agent-prof-id-params
                  ifi (ua/actor->ifi agent)]
              (lrsp/-set-document lrs tc/ctx auth-ident lrst/agent-prof-id-params lrst/agent-prof-doc true)
              (adp/-delete-actor lrs {:actor-ifi ifi})
              (is (nil? (:document (lrsp/-get-document lrs tc/ctx auth-ident {:profileId profileId
                                                                       :agent agent}))))))
          (testing "delete-actor: delete attachments of deleted statements"
            (let [ifi (ua/actor->ifi (lrst/stmt-4 "actor"))
                  stmt-id (u/str->uuid (lrst/stmt-4 "id"))]
              (lrsp/-store-statements lrs tc/ctx auth-ident [lrst/stmt-4] [lrst/stmt-4-attach])
              (adp/-delete-actor lrs {:actor-ifi ifi})
              (is (empty? (:attachments (lrsp/-get-statements lrs tc/ctx auth-ident {:statement_id stmt-id} []))))
              (testing "delete actor: delete statement-to-activity entries for deleted statements"
                (is (empty? (arb-query ["select * from statement_to_activity where statement_id  = ?" stmt-id]))))))))
      (finally (component/stop sys')))))

(deftest download-csv-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')
        hdrs [["id"] ["actor" "mbox"] ["verb" "id"] ["object" "id"]]]
    (try
      (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0 stmt-1 stmt-2] [])
      (testing "CSV Seq"
        (testing "- no params"
          (with-open [writer (java.io.StringWriter.)]
            (adp/-get-statements-csv lrs writer hdrs {})
            (let [stmt-str (str writer)
                  stmt-seq (csv/read-csv stmt-str)]
              (is (= ["id" "actor_mbox" "verb_id" "object_id"]
                     (first stmt-seq)))
              (is (= [(get stmt-2 "id")
                      (get-in stmt-2 ["actor" "mbox"] "") ;  is nil
                      (get-in stmt-2 ["verb" "id"])
                      (get-in stmt-2 ["object" "id"])]
                     (first (rest stmt-seq))))
              (is (= [(get stmt-1 "id")
                      (get-in stmt-1 ["actor" "mbox"])
                      (get-in stmt-1 ["verb" "id"])
                      (get-in stmt-1 ["object" "id"])]
                     (first (rest (rest stmt-seq)))))
              (is (= [(get stmt-0 "id")
                      (get-in stmt-0 ["actor" "mbox"])
                      (get-in stmt-0 ["verb" "id"])
                      (get-in stmt-0 ["object" "id"])]
                     (first (rest (rest (rest stmt-seq)))))))))
        (testing "- ascending set to true"
          (with-open [writer (java.io.StringWriter.)]
            (adp/-get-statements-csv lrs writer hdrs {:ascending true})
            (let [stmt-str (str writer)
                  stmt-seq (csv/read-csv stmt-str)]
              (is (not (realized? stmt-seq)))
              (is (= ["id" "actor_mbox" "verb_id" "object_id"]
                     (first stmt-seq)))
              (is (= [(get stmt-0 "id")
                      (get-in stmt-0 ["actor" "mbox"])
                      (get-in stmt-0 ["verb" "id"])
                      (get-in stmt-0 ["object" "id"])]
                     (first (rest stmt-seq))))
              (is (= [(get stmt-1 "id")
                      (get-in stmt-1 ["actor" "mbox"])
                      (get-in stmt-1 ["verb" "id"])
                      (get-in stmt-1 ["object" "id"])]
                     (first (rest (rest stmt-seq)))))
              (is (= [(get stmt-2 "id")
                      (get-in stmt-2 ["actor" "mbox"] "") ; is nil
                      (get-in stmt-2 ["verb" "id"])
                      (get-in stmt-2 ["object" "id"])]
                     (first (rest (rest (rest stmt-seq)))))))))
        (testing "- agent filter"
          (with-open [writer (java.io.StringWriter.)]
            (adp/-get-statements-csv lrs writer hdrs {:agent (-> (get stmt-2 "actor")
                                                                 (dissoc "name"))})
            (let [stmt-str (str writer)
                  stmt-seq (csv/read-csv stmt-str)]
              (is (= 2 (count stmt-seq)))
              (is (= [(get stmt-2 "id")
                      (get-in stmt-2 ["actor" "mbox"] "") ; is nil
                      (get-in stmt-2 ["verb" "id"])
                      (get-in stmt-2 ["object" "id"])]
                     (first (rest stmt-seq)))))))
        (testing "- verb filter"
          (with-open [writer (java.io.StringWriter.)]
            (adp/-get-statements-csv lrs writer hdrs {:verb (get-in stmt-2 ["verb" "id"])})
            (let [stmt-str (str writer)
                  stmt-seq (csv/read-csv stmt-str)]
              (is (= 2 (count stmt-seq)))
              (is (= [(get stmt-2 "id")
                      (get-in stmt-2 ["actor" "mbox"] "") ; is nil
                      (get-in stmt-2 ["verb" "id"])
                      (get-in stmt-2 ["object" "id"])]
                     (first (rest stmt-seq)))))))
        (testing "- entire database gets returned beyond `:limit`"
          (let [statements (->> #(assoc stmt-0 "id" (str (squuid/generate-squuid)))
                                (repeatedly 100))]
            (lrsp/-store-statements lrs tc/ctx auth-ident statements []))
          (with-open [writer (java.io.StringWriter.)]
            (adp/-get-statements-csv lrs writer hdrs {})
            (let [stmt-str (str writer)
                  stmt-seq (csv/read-csv stmt-str)]
              (is (= 104 (count stmt-seq)))))))
      (finally (component/stop sys')))))

;; TODO: Add tests for creds with no explicit scopes, once
;; `statements/read/mine` is implemented

(deftest auth-test
  (let [sys    (support/test-system)
        sys'   (component/start sys)
        {:keys [lrs backend]} sys'
        ds (get-in lrs [:connection :conn-pool])
        acc-id (:result (adp/-create-account lrs test-username test-password))]
    (try
      (testing "Credential creation"
        (let [{:keys [api-key secret-key] :as key-pair}
              (adp/-create-api-keys lrs acc-id nil #{"all" "all/read"})
              {credential-id :cred_id} (jdbc/with-transaction [tx ds]
                                         (bp/-query-credential-ids backend tx {:api-key api-key
                                                                               :secret-key secret-key}))]
          (is (re-matches Base64RegEx api-key))
          (is (re-matches Base64RegEx secret-key))
          (is (= {:api-key    api-key
                  :secret-key secret-key
                  :scopes     #{"all" "all/read"}}
                 key-pair))
          (testing "and credential retrieval"
            (is (= (adp/-get-api-keys lrs acc-id)
                   [{:api-key api-key
                     :secret-key secret-key
                     :label      nil
                     :scopes     #{"all" "all/read"}
                     :id         credential-id}]
                   (adp/-get-api-keys lrs acc-id))))
          (testing "and credential update"
            (is (= {:api-key    api-key
                    :secret-key secret-key
                    :label      "My Label"
                    :scopes     #{"all/read"
                                  "statements/read"
                                  "statements/read/mine"}}
                   (adp/-update-api-keys
                    lrs
                    acc-id
                    api-key
                    secret-key
                    "My Label"
                    #{"all/read" "statements/read" "statements/read/mine"})))
            (is (= (adp/-get-api-keys lrs acc-id)
                   [{:api-key api-key
                     :secret-key secret-key
                     :label      "My Label"
                     :scopes     #{"all/read"
                                   "statements/read"
                                   "statements/read/mine"}
                     :id credential-id}]
                   (adp/-get-api-keys lrs acc-id))))
          (testing "and credential deletion"
            (adp/-delete-api-keys lrs acc-id api-key secret-key)
            (is (= []
                   (adp/-get-api-keys lrs acc-id))))))
      (finally (component/stop sys')))))

(defn- get-last-stored
  [lrs ctx auth-ident]
  (get-in
   (lrsp/-get-statements lrs ctx auth-ident {} [])
   [:statement-result
    :statements
    0
    "stored"]))

(defn- snap-day
  [timestamp]
  (-> timestamp
      (subs 0 10)
      u/pad-time-str))

(deftest status-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')]
    (try
      (testing "Get LRS status"
        (is (= {:statement-count       0
                :actor-count           0
                :last-statement-stored nil
                :platform-frequency    {}
                :timeline              []}
               (adp/-get-status lrs {})))
        ;; add a statement
        (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-0] [])
        (let [last-stored-0 (get-last-stored lrs tc/ctx auth-ident)
              day-0         (snap-day last-stored-0)]
          (is (= {:statement-count       1
                  :actor-count           1
                  :last-statement-stored last-stored-0
                  :platform-frequency    {"example" 1}
                  :timeline              [{:stored day-0
                                           :count  1}]}
                 (adp/-get-status lrs {})))
          ;; add another
          (lrsp/-store-statements lrs tc/ctx auth-ident [stmt-1] [])
          (let [last-stored-1 (get-last-stored lrs tc/ctx auth-ident)
                day-1         (snap-day last-stored-1)]
            (is (= {:statement-count       2  ;; increments
                    :actor-count           1  ;; same
                    :last-statement-stored last-stored-1 ;; increments
                    :platform-frequency    {"example"         1
                                            ;; new platform
                                            "another_example" 1}
                    :timeline              (if (= day-0 day-1)
                                             [{:stored day-0
                                               :count  2}]
                                             ;; unlikely, but hey
                                             [{:stored day-0
                                               :count  1}
                                              {:stored day-1
                                               :count  1}])}
                   (adp/-get-status lrs {}))))))
      (finally (component/stop sys')))))

(defn- strip-reaction-results
  [reaction-rows]
  (into #{}
        (map
         (fn [row]
           (select-keys row [:id :title :ruleset :active]))
         reaction-rows)))

(deftest reaction-manager-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')]
    (try
      (let [{create-result-0 :result}
            (adp/-create-reaction
             lrs "reaction-0" tc/simple-reaction-ruleset true)
            {create-result-1 :result}
            (adp/-create-reaction
             lrs "reaction-1" tc/simple-reaction-ruleset true)]
        (testing "Create reaction"
          (is (uuid? create-result-0))
          (is (uuid? create-result-1))
          (testing "title is unique"
            (is (= :lrsql.reaction/title-conflict-error
                   (:result (adp/-create-reaction
                             lrs "reaction-0" tc/simple-reaction-ruleset true))))))
        (testing "Get all reactions"
          (is (= #{{:id      create-result-0
                    :title   "reaction-0"
                    :ruleset tc/simple-reaction-ruleset
                    :active  true}
                   {:id      create-result-1
                    :title   "reaction-1"
                    :ruleset tc/simple-reaction-ruleset
                    :active  true}}
                 (strip-reaction-results
                  (adp/-get-all-reactions lrs)))))
        (testing "Update reaction"
          (is (= {:result :lrsql.reaction/reaction-not-found-error}
                 (adp/-update-reaction
                  lrs (u/generate-squuid) nil tc/simple-reaction-ruleset false)))
          ;; Make inactive
          (is (= {:result create-result-0}
                 (adp/-update-reaction
                  lrs create-result-0 nil nil false)))
          (is (= #{{:id      create-result-0
                    :title   "reaction-0"
                    :ruleset tc/simple-reaction-ruleset
                    :active  false}
                   {:id      create-result-1
                    :title   "reaction-1"
                    :ruleset tc/simple-reaction-ruleset
                    :active  true}}
                 (strip-reaction-results
                  (adp/-get-all-reactions lrs))))
          ;; Make active again
          (is (= {:result create-result-0}
                 (adp/-update-reaction
                  lrs create-result-0 nil nil true)))
          (is (= #{{:id      create-result-0
                    :title   "reaction-0"
                    :ruleset tc/simple-reaction-ruleset
                    :active  true}
                   {:id      create-result-1
                    :title   "reaction-1"
                    :ruleset tc/simple-reaction-ruleset
                    :active  true}}
                 (strip-reaction-results
                  (adp/-get-all-reactions lrs))))
          (testing "title is unique"
            (is (= :lrsql.reaction/title-conflict-error
                   (:result (adp/-update-reaction
                             lrs create-result-0 "reaction-1" nil false))))))
        (testing "Delete reaction"
          (is (= {:result :lrsql.reaction/reaction-not-found-error}
                 (adp/-delete-reaction lrs (u/generate-squuid))))
          (is (= {:result create-result-0}
                 (adp/-delete-reaction lrs create-result-0)))
          (is (= {:result create-result-1}
                 (adp/-delete-reaction lrs create-result-1)))
          (is (= []
                 (adp/-get-all-reactions lrs)))))
      (finally
        (component/stop sys')))))
