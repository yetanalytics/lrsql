(ns lrsql.admin.route-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

;; Init

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;; Test content

(def content-type {"Content-Type" "application/json"})

(defn- login-account
  [headers body]
  (curl/post "http://0.0.0.0:8080/admin/account/login"
             {:headers headers
              :body    body}))

(defn- create-account
  [headers body]
  (curl/post "http://0.0.0.0:8080/admin/account/create"
             {:headers headers
              :body    body}))

(defn- get-env
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/env"
            {:headers headers}))

(defn- get-account
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/account"
            {:headers headers}))

(defn- delete-account
  [headers body]
  (curl/delete "http://0.0.0.0:8080/admin/account"
               {:headers headers
                :body    body}))

;; Tests

(defmacro is-err-code
  "Test that `expr` throws an exception with the correct HTTP error `code`."
  [expr code]
  `(try
     (do ~expr (is false))
     (catch clojure.lang.ExceptionInfo e#
       (is (= ~code (-> e# ex-data :status))))))

(deftest admin-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        ;; Seed information
        {:keys [api-key-default
                api-secret-default]} (get-in sys' [:lrs :config])
        seed-body (String. (u/write-json
                            {"username" api-key-default
                             "password" api-secret-default}))
        seed-jwt  (-> (login-account content-type seed-body)
                      :body
                      u/parse-json
                      (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        ;; New data information
        headers  (merge content-type seed-auth)
        req-body (String. (u/write-json {"username" "myname"
                                         "password" "swordfish"}))]
    (testing "seed jwt retrieved"
      ;; Sanity check that the test credentials are in place
      (is (some? seed-jwt)))
    (testing "got environment vars"
      (let [{:keys [status body]} (get-env content-type)
            edn-body (u/parse-json body)]
        (is (= 200 status))
        (is (= (get edn-body "url-prefix") "/xapi"))
        (is (= (get edn-body "enable-stmt-html") true))))
    (testing "create account with username `myname` and password `swordfish`"
      (let [{:keys [status
                    body]} (create-account headers req-body)
            edn-body       (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false))))
      (is-err-code (create-account headers req-body) 409) ; Conflict
      (is-err-code (create-account headers "") 400)       ; Bad Request
      (is-err-code (create-account nil req-body) 400))
    (testing "get all admin accounts"
      (let [{:keys [status
                    body]} (get-account headers)
            edn-body       (u/parse-json body :object? false)]
        ;; success
        (is (= 200 status))
        ;; is a vec
        (is (vector? edn-body))
        ;; has the created user
        (is (some #(= (get % "username") "myname") edn-body))))
    (testing "log into the `myname` account"
      (let [{:keys [status body]}
            (login-account content-type req-body)
            edn-body
            (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false)))
        (is (re-matches #".*\..*\..*"
                        (get edn-body "json-web-token"))))
      ;; Parse errors
      (testing "- failure due to JSONEOFException"
        (let [bad-body "{\"username\": \"foo\", \"password\": \"bar}"]
          (is-err-code (login-account content-type bad-body) 400)))
      (testing "- failure due to JSONParseException"
        (let [bad-body "{\"username\": \"foo\", \"password\": bar\"}"]
          (is-err-code (login-account content-type bad-body) 400)))
      ;; Other errors
      (let [bad-body (String. (u/write-json
                               {"username" "foo"
                                "password" "swordfish"}))]
        ;; Bad User 401
        (is-err-code (login-account content-type bad-body) 401))
      (let [bad-body (String. (u/write-json
                               {"username" "myname"
                                "password" "badpass"}))]
        ;; Bad Pass 401
        (is-err-code (login-account content-type bad-body) 401))
      ;; Bad Request
      (let [bad-body ""]
        (is-err-code (login-account content-type bad-body) 400)))
    (testing "delete the `myname` account using the seed account"
      (let [del-jwt  (-> (login-account content-type req-body)
                         :body
                         u/parse-json
                         (get "json-web-token"))
            del-id   (-> (get-account headers)
                         :body
                         (u/parse-json :object? false)
                         (#(filter (fn [acc] (= (get acc "username") "myname")) %))
                         first
                         (get "account-id"))
            del-auth {"Authorization" (str "Bearer " del-jwt)}
            del-head (merge content-type del-auth)
            del-body (String. (u/write-json {"account-id" del-id}))]
        (let [delete-res (delete-account headers del-body)]
          (is (= 200 (:status delete-res)))
          (is (= del-id (-> delete-res
                            :body
                            u/parse-json
                            (get "account-id")))))
        (is-err-code (delete-account headers del-body) 404)
        (is-err-code (delete-account headers "") 400)
        (testing "using a deleted account ID for admin ops"
          (is-err-code (get-account del-head) 401) ; Unauthorized
          (is-err-code (create-account del-head req-body) 401)
          (is-err-code (delete-account del-head del-body) 401))
        (testing "using a deleted account ID for credential ops"
          (let [pk "foo"
                sk "bar"
                ss ["all" "all/read"]]
            (is-err-code (curl/post
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head
                           :body    (String. (u/write-json {"scopes" ss}))})
                         401)
            (is-err-code (curl/put
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head
                           :body    (String. (u/write-json {"api-key"    pk
                                                            "secret-key" sk
                                                            "scopes"     ss}))})
                         401)
            (is-err-code (curl/get
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head})
                         401)
            (is-err-code (curl/delete
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head
                           :body    (String. (u/write-json {"api-key"    pk
                                                            "secret-key" sk}))})
                         401)))))
    (component/stop sys')))

(deftest auth-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        jwt  (-> (curl/post "http://0.0.0.0:8080/admin/account/login"
                            {:headers content-type
                             :body    (String. (u/write-json
                                                {"username" "username"
                                                 "password" "password"}))})
                 :body
                 u/parse-json
                 (get "json-web-token"))
        auth {"Authorization" (str "Bearer " jwt)}
        hdr  (merge content-type auth)]
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
        (is (= #{"all" "all/read"} (set scopes)))
        (testing "and reading"
          (let [{:keys [status body]}
                (curl/get
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr})]
            (is (= 200 status))
            (is (= {"api-key"    api-key
                    "secret-key" secret-key
                    "scopes"     scopes}
                   (-> body
                       (u/parse-json :object? false)
                       (#(filter (fn [cred] (= (get cred "api-key") api-key))
                                 %))
                       first)))))
        (testing "and updating"
          (let [req-scopes
                ["all/read" "statements/read"]
                {:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body    (String. (u/write-json {"api-key"    api-key
                                                   "secret-key" secret-key
                                                   "scopes"     req-scopes}))})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{"all/read" "statements/read"} (set scopes)))))
        (testing "and reading after updating"
          (let [{:keys [status body]}
                (curl/get
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr})
                {api-key'    "api-key"
                 secret-key' "secret-key"
                 scopes'     "scopes"}
                (-> body
                    (u/parse-json :object? false)
                    (#(filter (fn [cred] (= (get cred "api-key") api-key)) %))
                    first)]
            (is (= 200 status))
            (is (= api-key api-key'))
            (is (= secret-key secret-key'))
            (is (= #{"all/read" "statements/read"} (set scopes')))))
        (testing "and no-op scope update"
          (let [req-scopes
                ["all/read" "statements/read"]
                {:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body   (String. (u/write-json {"api-key"    api-key
                                                  "secret-key" secret-key
                                                  "scopes"     req-scopes}))})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{"all/read" "statements/read"} (set scopes)))))
        (testing "and deleting all scopes"
          (let [{:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body   (String. (u/write-json {"api-key"    api-key
                                                  "secret-key" secret-key
                                                  "scopes"     []}))})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{} (set scopes)))))
        (testing "and deletion"
          (let [{:keys [status]}
                (curl/delete
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body    (String. (u/write-json {"api-key"    api-key
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
            (is (not (some (fn [cred] (= (get cred "api-key") api-key))
                           edn-res)))))))
    (component/stop sys')))
