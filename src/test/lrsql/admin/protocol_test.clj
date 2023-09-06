(ns lrsql.admin.protocol-test
  "Test the protocol fns of `AdminAccountManager`, `APIKeyManager`, `AdminStatusProvider` directly."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.admin.protocol :as adp]
            [lrsql.test-support   :as support]
            [lrsql.util           :as u]
            [lrsql.test-constants :as tc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def test-username "DonaldChamberlin123") ; co-inventor of SQL

(def test-password "iLoveSql")

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest admin-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (:lrs sys')]
    (testing "Admin account insertion"
      (is (-> (adp/-create-account lrs test-username test-password)
              :result
              uuid?))
      (is (-> (adp/-create-account lrs test-username test-password)
              :result
              (= :lrsql.admin/existing-account-error))))
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
    (testing "Admin password update"
      (let [account-id   (-> (adp/-authenticate-account lrs
                                                        test-username
                                                        test-password)
                             :result)
            new-password "iLoveNoSql"]
        (testing "Valid update"
          (adp/-update-admin-password lrs account-id test-password new-password)
          (is (-> (adp/-authenticate-account lrs
                                             test-username
                                             new-password)
                  :result
                  (= account-id))))
        (testing "Invalid update"
          (is (-> (adp/-update-admin-password
                   lrs account-id "iLoveMongoDB" test-password)
                  :result
                  (= :lrsql.admin/invalid-password-error))))
        ;; Change it back for subsequent tests
        (adp/-update-admin-password
         lrs account-id new-password test-password)))
    (testing "Admin account deletion"
      (let [account-id (-> (adp/-authenticate-account lrs
                                                      test-username
                                                      test-password)
                           :result)]
        (testing "When OIDC is off"
          (let [oidc-enabled? false]
            (testing "Succeeds if there is more than one account"
              (adp/-delete-account lrs account-id oidc-enabled?)
              (is (-> (adp/-authenticate-account lrs test-username test-password)
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
    (component/stop sys')))

;; TODO: Add tests for creds with no explicit scopes, once
;; `statements/read/mine` is implemented

(deftest auth-test
  (let [sys    (support/test-system)
        sys'   (component/start sys)
        lrs    (:lrs sys')
        acc-id (:result (adp/-create-account lrs test-username test-password))]
    (testing "Credential creation"
      (let [{:keys [api-key secret-key] :as key-pair}
            (adp/-create-api-keys lrs acc-id #{"all" "all/read"})]
        (is (re-matches Base64RegEx api-key))
        (is (re-matches Base64RegEx secret-key))
        (is (= {:api-key    api-key
                :secret-key secret-key
                :scopes     #{"all" "all/read"}}
               key-pair))
        (testing "and credential retrieval"
          (is (= [{:api-key    api-key
                   :secret-key secret-key
                   :scopes     #{"all" "all/read"}}]
                 (adp/-get-api-keys lrs acc-id))))
        (testing "and credential update"
          (is (= {:api-key    api-key
                  :secret-key secret-key
                  :scopes     #{"all/read"
                                "statements/read"
                                "statements/read/mine"}}
                 (adp/-update-api-keys
                  lrs
                  acc-id
                  api-key
                  secret-key
                  #{"all/read" "statements/read" "statements/read/mine"})))
          (is (= [{:api-key    api-key
                   :secret-key secret-key
                   :scopes     #{"all/read"
                                 "statements/read"
                                 "statements/read/mine"}}]
                 (adp/-get-api-keys lrs acc-id))))
        (testing "and credential deletion"
          (adp/-delete-api-keys lrs acc-id api-key secret-key)
          (is (= []
                 (adp/-get-api-keys lrs acc-id))))))
    (component/stop sys')))

(defn- get-last-stored
  [lrs auth-ident]
  (get-in
   (lrsp/-get-statements lrs auth-ident {} [])
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
    (testing "Get LRS status"
      (is (= {:statement-count       0
              :actor-count           0
              :last-statement-stored nil
              :platform-frequency    {}
              :timeline              []}
             (adp/-get-status lrs {})))
      ;; add a statement
      (lrsp/-store-statements lrs auth-ident [stmt-0] [])
      (let [last-stored-0 (get-last-stored lrs auth-ident)
            day-0         (snap-day last-stored-0)]
        (is (= {:statement-count       1
                :actor-count           1
                :last-statement-stored last-stored-0
                :platform-frequency    {"example" 1}
                :timeline              [{:stored day-0
                                         :count  1}]}
               (adp/-get-status lrs {})))
        ;; add another
        (lrsp/-store-statements lrs auth-ident [stmt-1] [])
        (let [last-stored-1 (get-last-stored lrs auth-ident)
              day-1         (snap-day last-stored-1)]
          (is (= {:statement-count       2 ;; increments
                  :actor-count           1 ;; same
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
    (component/stop sys')))

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
