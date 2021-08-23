(ns lrsql.admin.protocol-test
  "Test the protocol fns of `AdminAccountManager` and `APIKeyManager` directly."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component    :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.admin.protocol :as adp]
            [lrsql.test-support   :as support]))

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

(def test-username "DonaldChamberlin123") ; co-inventor of SQL
(def test-password "iLoveSql")

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
    (testing "Admin account deletion"
      (let [account-id (-> (adp/-authenticate-account lrs
                                                      test-username
                                                      test-password)
                           :result)]
        (adp/-delete-account lrs account-id)
        (is (-> (adp/-authenticate-account lrs test-username test-password)
                :result
                (= :lrsql.admin/missing-account-error)))))
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
                  :scopes     #{"all/read" "statements/read"}}
                 (adp/-update-api-keys
                  lrs
                  acc-id
                  api-key
                  secret-key
                  #{"all/read" "statements/read"})))
          (is (= [{:api-key    api-key
                   :secret-key secret-key
                   :scopes     #{"all/read" "statements/read"}}]
                 (adp/-get-api-keys lrs acc-id))))
        (testing "and credential deletion"
          (adp/-delete-api-keys lrs acc-id api-key secret-key)
          (is (= []
                 (adp/-get-api-keys lrs acc-id))))))
    (component/stop sys')))
