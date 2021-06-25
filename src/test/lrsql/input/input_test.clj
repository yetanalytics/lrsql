(ns lrsql.input.input-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.input.actor      :as i-ac]
            [lrsql.input.activity   :as i-av]
            [lrsql.input.attachment :as i-at]
            [lrsql.input.admin      :as i-admin]
            [lrsql.input.auth       :as i-auth]
            [lrsql.input.statement  :as i-stmt]
            [lrsql.input.document   :as i-doc]))

(deftest test-insert-inputs
  (testing "statement object insertion inputs"
    (is (nil? (check-validate `i-ac/actor-insert-input)))
    (is (nil? (check-validate `i-ac/group-insert-input)))
    (is (nil? (check-validate `i-av/activity-insert-input)))
    (is (nil? (check-validate `i-ac/statement-to-actor-insert-input)))
    (is (nil? (check-validate `i-av/statement-to-activity-insert-input))))
  (testing "statement insertion inputs"
    (is (nil? (check-validate `i-stmt/statement-insert-inputs 10)))
    (is (nil? (check-validate `i-stmt/statements-insert-inputs 5))))
  (testing "descendant insertion inputs"
    (is (nil? (check-validate `i-stmt/descendant-insert-input)))
    (is (nil? (check-validate `i-stmt/add-descendant-insert-inputs 10))))
  (testing "attachment insertion inputs"
    (is (nil? (check-validate `i-at/attachment-insert-input)))
    (is (nil? (check-validate `i-stmt/add-attachment-insert-inputs 10))))
  (testing "document insertion inputs"
    (is (nil? (check-validate `i-doc/document-insert-input)))))

(deftest test-query-inputs
  (testing "statement query inputs"
    (is (nil? (check-validate `i-stmt/statement-query-input)))
    (is (nil? (check-validate `i-ac/agent-query-input)))
    (is (nil? (check-validate `i-av/activity-query-input))))
  (testing "document query inputs"
    (is (nil? (check-validate `i-doc/document-input)))
    (is (nil? (check-validate `i-doc/document-ids-input)))
    (is (nil? (check-validate `i-doc/document-multi-input)))))

(deftest test-auth
  (testing "authentication inputs"
    (is (nil? (check-validate `i-auth/insert-credential-input)))
    (is (nil? (check-validate `i-auth/insert-credential-scopes-input)))
    (is (nil? (check-validate `i-auth/delete-credentials-input)))
    (is (nil? (check-validate `i-auth/delete-credential-scopes-input)))
    (is (nil? (check-validate `i-auth/query-credentials-input)))
    (is (nil? (check-validate `i-auth/query-credential-scopes-input)))))

(deftest test-admin
  (testing "admin account inputs"
    (is (nil? (check-validate `i-admin/admin-insert-input)))
    (is (nil? (check-validate `i-admin/admin-query-input)))
    (is (nil? (check-validate `i-admin/admin-delete-input)))))
