(ns lrsql.admin.route-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

(def content-type {"Content-Type" "application/json"})

(deftest admin-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        {:keys [api-key-default api-secret-default]} (get-in sys' [:lrs :config])
        seed-jwt (-> (curl/post "http://0.0.0.0:8080/admin/account/login"
                                {:headers content-type
                                 :body
                                 (String. (u/write-json
                                           {"username" api-key-default
                                            "password" api-secret-default}))})
                     :body
                     u/parse-json
                     (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        data {:body (String. (u/write-json {"username" "myname"
                                            "password" "swordfish"}))}]
    (testing "seed jwt retrieved"
      ;; Sanity check that the test credentials are in place
      (is (some? seed-jwt)))
    (testing "admin account creation"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/account/create"
                       (assoc data :headers (merge content-type seed-auth)))
            edn-body
            (u/parse-json body)]
        (is (= 200 status))
        (is (try (u/str->uuid (get edn-body "account-id"))
                 (catch Exception _ false))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   (assoc data :headers (merge content-type seed-auth)))
        (catch clojure.lang.ExceptionInfo e
          ;; Conflict status code
          (is (= 409 (-> e ex-data :status)))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   {:body ""
                    :headers (merge content-type seed-auth)})
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code (invalid body)
          (is (= 400 (-> e ex-data :status)))))
      (try
        (curl/post "http://0.0.0.0:8080/admin/account/create"
                   (assoc data :headers nil))
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code (missing header and jwt)
          (is (= 400 (-> e ex-data :status))))))
    (testing "get admin accounts"
      (let [{:keys [status body]}
            (curl/get "http://0.0.0.0:8080/admin/account"
                      {:headers (merge content-type seed-auth)})
            edn-body
            (u/parse-json body :object? false)]
        ;; success
        (is (= 200 status))
        ;; is a vec
        (is (vector? edn-body))
        ;; has the created user
        (is (some #(= (get % "username") "myname") edn-body))))
    (testing "admin account login"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/account/login"
                       (assoc data :headers content-type))
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
         {:body (String. (u/write-json
                          {"username" "foo"
                           "password" "swordfish"}))
          :headers content-type})
        (catch clojure.lang.ExceptionInfo e
          ;; Not Found status code
          (is (= 404 (-> e ex-data :status)))))
      (try
        (curl/post
         "http://0.0.0.0:8080/admin/account/login"
         {:body (String. (u/write-json
                          {"username" "myname"
                           "password" "badpass"}))
          :headers content-type})
        (catch clojure.lang.ExceptionInfo e
          ;; Forbidden status code
          (is (= 401 (-> e ex-data :status)))))
      (try
        (curl/post
         "http://0.0.0.0:8080/admin/account/login"
         {:body ""
          :headers content-type})
        (catch clojure.lang.ExceptionInfo e
          ;; Bad Request status code
          (is (= 400 (-> e ex-data :status))))))
    (testing "admin account deletion"
      (let [del-acc-id (-> (curl/get "http://0.0.0.0:8080/admin/account"
                                     {:headers (merge content-type seed-auth)})
                           :body
                           (u/parse-json :object? false)
                           (#(filter (fn [acct]
                                       (= (get acct "username") "myname")) %))
                           first
                           (get "account-id"))
            del-jwt    (-> (curl/post "http://0.0.0.0:8080/admin/account/login"
                                      (merge {:headers content-type} data))
                           :body
                           u/parse-json
                           (get "json-web-token"))
            del-auth   {"Authorization" (str "Bearer " del-jwt)}
            del-data   {:body (String. (u/write-json {"account-id" del-acc-id}))}]
        (let [delete-res
              (curl/delete "http://0.0.0.0:8080/admin/account"
                           (assoc del-data
                                  :headers (merge content-type seed-auth)))]
          (is (= 200 (:status delete-res))))
        (try
          (curl/delete "http://0.0.0.0:8080/admin/account"
                       (assoc del-data
                              :headers (merge content-type seed-auth)))
          (catch clojure.lang.ExceptionInfo e
            ;; Not Found status code
            (is (= 404 (-> e ex-data :status)))))
        (try
          (curl/delete
           "http://0.0.0.0:8080/admin/account"
           {:body ""
            :headers (merge content-type seed-auth)})
          (catch clojure.lang.ExceptionInfo e
            ;; Bad Request status code
            (is (= 400 (-> e ex-data :status)))))
        (testing "doing stuff after deletion"
          (try
            (curl/get "http://0.0.0.0:8080/admin/account"
                      {:headers (merge content-type del-auth)})
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status)))))
          (try
            (curl/post "http://0.0.0.0:8080/admin/account/create"
                       (assoc data :headers (merge content-type del-auth)))
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status)))))
          (try
            (curl/delete "http://0.0.0.0:8080/admin/account"
                         (assoc del-data
                                :headers (merge content-type del-auth)))
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status))))))
        (testing "doing stuff after deletion"
          (try
            (curl/post "http://0.0.0.0:8080/admin/creds"
                       {:headers (merge content-type del-auth)
                        :body    (String. (u/write-json
                                           {"scopes" ["all" "all/read"]}))})
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status)))))
          (try
            (curl/put "http://0.0.0.0:8080/admin/creds"
                      {:headers (merge content-type del-auth)
                       :body    (String. (u/write-json
                                          {"api-key" "foo"
                                           "secret-key" "bar"
                                           "scopes" ["all" "all/read"]}))})
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status)))))
          (try
            (curl/get "http://0.0.0.0:8080/admin/creds"
                      {:headers (merge content-type del-auth)})
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status)))))
          (try
            (curl/delete "http://0.0.0.0:8080/admin/creds"
                         {:headers (merge content-type del-auth)
                          :body    (String. (u/write-json
                                             {"api-key" "foo"
                                              "secret-key" "bar"}))})
            (catch clojure.lang.ExceptionInfo e
              ;; Unauthorized status code
              (is (= 401 (-> e ex-data :status))))))))
    (component/stop sys')))

(deftest auth-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        jwt  (->
              (curl/post "http://0.0.0.0:8080/admin/account/login"
                         {:headers {"Content-Type" "application/json"}
                          :body    (String. (u/write-json
                                             {"username" "username"
                                              "password" "password"}))})
              :body
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
                   (-> body
                       (u/parse-json :object? false)
                       (#(filter (fn [cred] (= (get cred "api-key") api-key)) %))
                       first)))))
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
                (-> body
                    (u/parse-json :object? false)
                    (#(filter (fn [cred] (= (get cred "api-key") api-key)) %))
                    first)]
            (is (= 200 status))
            (is (= api-key api-key'))
            (is (= secret-key secret-key'))
            (is (= #{"all/read" "statements/read"} (set scopes')))))
        (testing "and no-op scope update"
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
        (testing "and deleting all scopes"
          (let [{:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body (String.
                         (u/write-json
                          {"api-key"    api-key
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
            (is (not (some (fn [cred] (= (get cred "api-key") api-key))
                           edn-res)))))))
    (component/stop sys')))
