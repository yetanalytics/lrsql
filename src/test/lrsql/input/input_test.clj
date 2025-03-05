(ns lrsql.input.input-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.input.actor        :as i-ac]
            [lrsql.input.activity     :as i-av]
            [lrsql.input.attachment   :as i-at]
            [lrsql.input.admin        :as i-admin]
            [lrsql.input.admin.jwt    :as i-adm-jwt]
            [lrsql.input.admin.status :as i-adm-stat]
            [lrsql.input.auth         :as i-auth]
            [lrsql.input.statement    :as i-stmt]
            [lrsql.input.document     :as i-doc]))

(deftest test-insert-inputs
  (testing "statement object insertion inputs"
    (is (nil? (check-validate `i-ac/insert-actor-input)))
    (is (nil? (check-validate `i-ac/insert-group-input)))
    (is (nil? (check-validate `i-av/insert-activity-input)))
    (is (nil? (check-validate `i-ac/insert-statement-to-actor-input)))
    (is (nil? (check-validate `i-av/insert-statement-to-activity-input))))
  (testing "statement insertion inputs"
    (is (nil? (check-validate `i-stmt/insert-statement-input 10)))
    (is (nil? (check-validate `i-stmt/insert-statement-batch-input 5))))
  (testing "descendant insertion inputs"
    (is (nil? (check-validate `i-stmt/insert-descendant-input)))
    (is (nil? (check-validate `i-stmt/add-insert-descendant-inputs 10))))
  (testing "attachment insertion inputs"
    (is (nil? (check-validate `i-at/insert-attachment-input)))
    (is (nil? (check-validate `i-stmt/add-insert-attachment-inputs 10))))
  (testing "document insertion inputs"
    (is (nil? (check-validate `i-doc/insert-document-input)))))

(deftest test-query-inputs
  (testing "statement query inputs"
    (is (nil? (check-validate `i-stmt/query-statement-input)))
    (is (nil? (check-validate `i-ac/query-agent-input)))
    (is (nil? (check-validate `i-av/query-activity-input))))
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
    (is (nil? (check-validate `i-auth/query-credential-scopes*-input)))
    (is (nil? (check-validate `i-auth/query-credential-scopes-input 5)))))

(deftest test-admin
  (testing "admin account inputs"
    (is (nil? (check-validate `i-admin/insert-admin-input 3)))
    (is (nil? (check-validate `i-admin/insert-admin-oidc-input 3)))
    (is (nil? (check-validate `i-admin/query-validate-admin-input)))
    (is (nil? (check-validate `i-admin/query-admin-exists-input)))
    (is (nil? (check-validate `i-admin/delete-admin-input)))))

(deftest test-admin-jwt
  (testing "admin JWT inputs"
    (is (nil? (check-validate `i-adm-jwt/query-blocked-jwt-input)))
    (is (nil? (check-validate `i-adm-jwt/insert-blocked-jwt-input)))))

(deftest test-admin-status
  (testing "admin status inputs"
    (is (nil? (check-validate `i-adm-stat/query-timeline-input)))
    (is (nil? (check-validate `i-adm-stat/query-status-input)))))
