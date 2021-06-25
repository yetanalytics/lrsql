(ns lrsql.util.admin-util-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.admin :as ua]
            [lrsql.util :as u]))

;; TODO: Replace with a gentest
(deftest password-test
  (testing "password hashing and verification"
    (let [pass-hash-m (ua/hash-password "foo")]
      (is (ua/valid-password? "foo" pass-hash-m))
      (is (not (ua/valid-password? "pass" pass-hash-m))))))

;; TODO: Test JWT expiry
(deftest jwt-test
  (let [test-id (u/str->uuid "00000000-0000-0000-0000-000000000001")]
    (testing "JSON web tokens"
      (is (re-matches #"\w*\.\w*\.\w*" (ua/account-id->jwt test-id)))
      (is (= test-id
             (-> test-id ua/account-id->jwt ua/jwt->account-id)))
      (is (nil? (ua/jwt->account-id "not-a-jwt"))))))
