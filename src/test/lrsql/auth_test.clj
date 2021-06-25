(ns lrsql.auth-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.system         :as system]
            [lrsql.test-support   :as support]))

(use-fixtures :each support/fresh-db-fixture)

(def test-username "DonaldChamberlin123") ; co-inventor of SQL
(def test-password "iLoveSql")

(deftest admin-test
  (let [_     (support/assert-in-mem-db)
        sys   (system/system :test)
        sys'  (component/start sys)
        lrs   (:lrs sys')]
    (testing "Admin account insertion"
      (is (-> (adp/-create-account lrs test-username test-password)
              :result
              uuid?))
      (is (-> (adp/-create-account lrs test-username test-password)
              :result
              (= :lrsql.admin/existing-account-error))))
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
