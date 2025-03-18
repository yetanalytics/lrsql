(ns lrsql.util.admin-test
  (:require [clojure.test :refer [deftest testing is]]
            [java-time.api :as jt]
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

(defn- account-id->jwt*
  [test-id ref-time]
  (ua/account-id->jwt* test-id "secret" 3600 ref-time))

(defn- one-time-jwt
  ([]
   (one-time-jwt {}))
  ([claim]
   (ua/one-time-jwt claim "secret" 3600)))

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
               :refresh-exp)))
      (let [utime (-> test-id
                      account-id->jwt
                      jwt->payload
                      :refresh-exp)]
        (is (= utime
               (-> (account-id->jwt* test-id utime)
                   jwt->payload
                   :refresh-exp))))
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
               (ua/jwt->payload tok "secret" 0)))))
    (testing "One-time JWTs"
      (let [{:keys [jwt exp oti]} (one-time-jwt)]
        (is (int? exp))
        (is (uuid? oti))
        (is (inst? (-> jwt jwt->payload :expiration)))
        (is (uuid? (-> jwt jwt->payload :one-time-id)))
        (is (= oti (-> jwt jwt->payload :one-time-id))))
      (let [expiration (jt/truncate-to (u/current-time) :seconds)
            {:keys [jwt exp oti]} (one-time-jwt {:account-id test-id
                                                 :expiration expiration})]
        (is (int? exp))
        (is (uuid? oti))
        (is (= expiration
               (-> jwt jwt->payload :expiration)))))))
