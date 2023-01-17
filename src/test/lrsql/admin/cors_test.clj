(ns lrsql.admin.cors-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def content-type {"Content-Type" "application/json"})

(defn- login-account
  [headers body]
  (curl/post "http://localhost:8080/admin/account/login"
             {:headers headers
              :body    body}))

(defn- create-account
  [headers body]
  (curl/post "http://localhost:8080/admin/account/create"
             {:headers headers
              :body    body}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro is-err-code
  "Test that `expr` throws an exception with the correct HTTP error `code`."
  [expr code]
  `(try
     (do ~expr (is false))
     (catch clojure.lang.ExceptionInfo e#
       (is (= ~code (-> e# ex-data :status))))))

(deftest cors-default-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        ;; Seed information
        {:keys [api-key-default
                api-secret-default]} (get-in sys' [:lrs :config])
        seed-body (u/write-json-str
                   {"username" api-key-default
                    "password" api-secret-default})
        seed-jwt  (-> (login-account content-type seed-body)
                      :body
                      u/parse-json
                      (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        ;; New data information
        headers  (merge content-type seed-auth)
        req-body (u/write-json-str {"username" "newUsername"
                                    "password" "newPass"})]
    (testing "seed jwt retrieved"
      ;; Sanity check that the test credentials are in place
      (is (some? seed-jwt)))
    (testing "create account with default CORS check failure"
      (let [bad-cors-headers
            (merge headers {"Origin" "http://www.yetanalytics.com"})]
        (is-err-code (create-account bad-cors-headers req-body) 403)))
    (testing "create account with default CORS check success"
      (let [good-cors-headers
            (merge headers {"Origin" "http://localhost:8080"})
            {:keys [status body]}
            (create-account good-cors-headers req-body)
            edn-body       (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false)))))
    (component/stop sys')))

(deftest cors-custom-test
  (let [sys  (support/test-system :conf-overrides
                                  {[:webserver :allowed-origins]
                                   ["http://www.yetanalytics.com"]})
        sys' (component/start sys)
        ;; Seed information
        {:keys [api-key-default
                api-secret-default]} (get-in sys' [:lrs :config])
        seed-body (u/write-json-str
                   {"username" api-key-default
                    "password" api-secret-default})
        seed-jwt  (-> (login-account content-type seed-body)
                      :body
                      u/parse-json
                      (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        ;; New data information
        headers  (merge content-type seed-auth)
        req-body (u/write-json-str {"username" "newUsername"
                                    "password" "newPass"})]
    (testing "seed jwt retrieved"
      ;; Sanity check that the test credentials are in place
      (is (some? seed-jwt)))
    (testing "create account with custom CORS check failure"
      (let [bad-cors-headers
            (merge headers {"Origin" "http://localhost:8080"})]
        (is-err-code (create-account bad-cors-headers req-body) 403)))
    (testing "create account with custom CORS check success"
      (let [good-cors-headers
            (merge headers {"Origin" "http://www.yetanalytics.com"})
            {:keys [status body]}
            (create-account good-cors-headers req-body)
            edn-body       (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false)))))
    (component/stop sys')))
