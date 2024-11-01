(ns lrsql.util.admin-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.admin :as ua]
            [lrsql.util :as u]))

(deftest password-test
  (testing "password hashing and verification"
    (let [pass-hash-m (ua/hash-password "foo")]
      (is (ua/valid-password? "foo" pass-hash-m))
      (is (not (ua/valid-password? "pass" pass-hash-m))))))

(defn- account-id->jwt
  [test-id]
  (ua/account-id->jwt test-id "secret" 3600 86400))

(defn- jwt->payload
  [jwt]
  (ua/jwt->payload jwt "secret" 1))

(deftest jwt-test
  (let [test-id (u/str->uuid "00000000-0000-1000-0000-000000000001")]
    (testing "JSON web tokens"
      (is (re-matches #".*\..*\..*" (account-id->jwt test-id)))
      (is (= test-id
             (-> test-id
                 account-id->jwt
                 jwt->payload
                 :account-id)))
      (is (inst?
           (-> test-id
               account-id->jwt
               jwt->payload
               :expiration)))
      (is (inst?
           (-> test-id
               account-id->jwt
               jwt->payload
               :ultimate)))
      (is (= :lrsql.admin/unauthorized-token-error
             (jwt->payload nil)))
      (is (= :lrsql.admin/unauthorized-token-error
             (jwt->payload "not-a-jwt")))
      (is (= :lrsql.admin/unauthorized-token-error
             (-> test-id
                 account-id->jwt
                 (ua/jwt->payload "different-secret" 1))))
      (is (= :lrsql.admin/unauthorized-token-error
             (let [tok (ua/account-id->jwt test-id "secret" 1 100)
                   _   (Thread/sleep 1001)]
               (ua/jwt->payload tok "secret" 0)))))))
