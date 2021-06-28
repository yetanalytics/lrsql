(ns lrsql.admin.admin-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.test-support :refer [assert-in-mem-db]]
            [lrsql.util :as u]))

(deftest admin-routes-test
  (let [_    (assert-in-mem-db)
        sys  (component/start (system/system))
        data {:headers {"Content-Type" "application/json"}
              :body    (String. (u/write-json {"username" "myname"
                                               "password" "swordfish"}))}]
    (testing "admin account creation"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/account/create"
                       data)
            edn-body
            (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false)))
        (is (re-matches #".*\..*\..*"
                        (get edn-body "jwt"))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   data)
        (catch clojure.lang.ExceptionInfo e
          ;; Conflict status code
          (is (= 409 (-> e ex-data :status)))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   (assoc data :body ""))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code
          (is (= 400 (-> e ex-data :status))))))
    (testing "admin account login"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/account/login"
                       data)
            edn-body
            (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false)))
        (is (re-matches #".*\..*\..*"
                        (get edn-body "jwt"))))
      (try
        (curl/post
         "http://0.0.0.0:8080/admin/account/login"
         (assoc data :body (String. (u/write-json
                                     {"username" "foo"
                                      "password" "swordfish"}))))
        (catch clojure.lang.ExceptionInfo e
          ;; Not Found status code
          (is (= 404 (-> e ex-data :status)))))
      (try
        (curl/post
         "http://0.0.0.0:8080/admin/account/login"
         (assoc data :body (String. (u/write-json
                                     {"username" "myname"
                                      "password" "badpass"}))))
        (catch clojure.lang.ExceptionInfo e
          ;; Forbidden status code
          (is (= 401 (-> e ex-data :status)))))
      (try
        (curl/post
         "http://0.0.0.0:8080/admin/account/login"
         (assoc data :body ""))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code
          (is (= 400 (-> e ex-data :status))))))
    (testing "admin account deletion"
      (let [delete-res
            (curl/delete "http://0.0.0.0:8080/admin/account"
                         data)]
        (is (= 200 (:status delete-res))))
      (try
        (curl/delete
         "http://0.0.0.0:8080/admin/account"
         (assoc data :body ""))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code
          (is (= 400 (-> e ex-data :status))))))
    (component/stop sys)))
