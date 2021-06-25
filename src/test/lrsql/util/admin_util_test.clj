(ns lrsql.util.admin-util-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.admin :as ua]))

;; TODO: Replace with a gentest
(deftest password-test
  (testing "password hashing and verification"
    (let [pass-hash-m (ua/hash-password "foo")]
      (is (ua/valid-password? "foo" pass-hash-m))
      (is (not (ua/valid-password? "pass" pass-hash-m))))))

;; TODO: JWT tests
