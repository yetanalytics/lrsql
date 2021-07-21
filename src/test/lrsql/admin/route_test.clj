(ns lrsql.admin.route-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.system :as system]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

(deftest admin-routes-test
  (let [_    (support/assert-in-mem-db)
        sys  (system/system)
        sys' (component/start sys)
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
                        (get edn-body "json-web-token"))))
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
          ;; Bad Request status code (invalid body)
          (is (= 400 (-> e ex-data :status)))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   (assoc data :headers nil))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code (missing header and jwt)
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
                        (get edn-body "json-web-token"))))
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
        (curl/delete "http://0.0.0.0:8080/admin/account"
                     data)
        (catch clojure.lang.ExceptionInfo e
          ;; Not Found status code
          (is (= 404 (-> e ex-data :status)))))
      (try
        (curl/delete
         "http://0.0.0.0:8080/admin/account"
         (assoc data :body ""))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code
          (is (= 400 (-> e ex-data :status))))))
    (component/stop sys')))

(deftest auth-routes-test
  (let [_    (support/assert-in-mem-db)
        sys  (system/system)
        sys' (component/start sys)
        jwt  (->
              (curl/post "http://0.0.0.0:8080/admin/account/create"
                         {:headers {"Content-Type" "application/json"}
                          :body    (String. (u/write-json
                                             {"username" "myname"
                                              "password" "swordfish"}))})
              (get :body)
              u/parse-json
              (get "json-web-token"))
        hdr  {"Content-Type" "application/json"
              "Authorization" (str "Bearer " jwt)}]
    (testing "credential creation"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/creds"
                      {:headers hdr
                       :body (String. (u/write-json
                                       {"scopes" ["all" "all/read"]}))})
            {:strs [api-key secret-key scopes]}
            (u/parse-json body)]
        (is (= 200 status))
        (is (re-matches Base64RegEx api-key))
        (is (re-matches Base64RegEx secret-key))
        (is (= #{"all" "all/read"}
               (set scopes)))
        (testing "and reading"
          (let [{:keys [status body]}
                (curl/get
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr})]
            (is (= 200 status))
            (is (= {"api-key"    api-key
                    "secret-key" secret-key
                    "scopes"     scopes}
                   (first (u/parse-json body :object? false))))))
        (testing "and updating"
          (let [{:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body (String.
                         (u/write-json
                          {"api-key"    api-key
                           "secret-key" secret-key
                           "scopes"     ["all/read" "statements/read"]}))})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{"all/read" "statements/read"}
                   (set scopes)))))
        (testing "and reading after updating"
          (let [{:keys [status body]}
                (curl/get
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr})
                {api-key'    "api-key"
                 secret-key' "secret-key"
                 scopes'     "scopes"}
                (first (u/parse-json body :object? false))]
            (is (= 200 status))
            (is (= api-key api-key'))
            (is (= secret-key secret-key'))
            (is (= #{"all/read" "statements/read"} (set scopes')))
            (is (nil? (second (u/parse-json body :object? false))))))
        (testing "and deletion"
          (let [{:keys [status]}
                (curl/delete
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body    (String. (u/write-json
                                     {"api-key"    api-key
                                      "secret-key" secret-key}))})]
            (is (= 200 status))))
        (testing "and reading after deletion"
          (let [{:keys [status body]}
                (curl/get
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr})
                edn-res
                (u/parse-json body :object? false)]
            (is (= 200 status))
            (is (= [] edn-res))))))
    (component/stop sys')))
