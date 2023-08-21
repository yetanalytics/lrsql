(ns lrsql.admin.route-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]))

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

(defn- update-account-password
  [headers
   body]
  (curl/put "http://0.0.0.0:8080/admin/account/password"
            {:headers headers
             :body    body
             :throw   false}))

(defn- delete-account
  [headers body]
  (curl/delete "http://0.0.0.0:8080/admin/account"
               {:headers headers
                :body    body}))

(defn- get-status
  [headers]
  (curl/get "http://localhost:8080/admin/status"
            {:headers headers}))

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

(deftest admin-routes-test
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
        req-body (u/write-json-str {"username" "myname"
                                    "password" "swordfish"})]
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
      (let [bad-body (u/write-json-str {"username" "foo"
                                        "password" "swordfish"})]
        ;; Bad User 401
        (is-err-code (login-account content-type bad-body) 401))
      (let [bad-body (u/write-json-str {"username" "myname"
                                        "password" "badpass"})]
        ;; Bad Pass 401
        (is-err-code (login-account content-type bad-body) 401))
      ;; Bad Request
      (let [bad-body ""]
        (is-err-code (login-account content-type bad-body) 400)))
    (testing "change the password of the `myname` account"
      (let [update-jwt   (-> (login-account content-type req-body)
                             :body
                             u/parse-json
                             (get "json-web-token"))
            update-auth  {"Authorization" (str "Bearer " update-jwt)}
            update-head  (merge content-type update-auth)
            new-pass     "fnordfish"
            update-pass! (fn [payload]
                           (update-account-password
                            update-head
                            (u/write-json-str payload)))]
        (is (-> (update-pass! {"old-password" "swordfish"
                               "new-password" new-pass})
                :status
                (= 200)))
        (is (-> (login-account
                 content-type
                 (u/write-json-str {"username" "myname"
                                    "password" new-pass}))
                :status
                (= 200)))
        (testing "with the wrong password"
          (is (-> (update-pass! {"old-password" "verywrongpass"
                                 "new-password" "whocares"})
                  :status
                  (= 401))))
        (testing "without a change"
          (is (-> (update-pass! {"old-password" new-pass
                                 "new-password" new-pass})
                  :status
                  (= 400))))
        (testing "change it back"
          (is (-> (update-pass! {"old-password" new-pass
                                 "new-password" "swordfish"})
                  :status
                  (= 200))))))
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
            del-body (u/write-json-str {"account-id" del-id})]
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
                           :body    (u/write-json-str {"scopes" ss})})
                         401)
            (is-err-code (curl/put
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head
                           :body    (u/write-json-str {"api-key"    pk
                                                       "secret-key" sk
                                                       "scopes"     ss})})
                         401)
            (is-err-code (curl/get
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head})
                         401)
            (is-err-code (curl/delete
                          "http://0.0.0.0:8080/admin/creds"
                          {:headers del-head
                           :body    (u/write-json-str {"api-key"    pk
                                                       "secret-key" sk})})
                         401)))))
    (testing "get status information"
      (let [{:keys [status
                    body]} (get-status headers)
            edn-body       (u/parse-json body)]
        ;; success
        (is (= 200 status))
        ;; Has data
        (is (= {"statement-count"       0
                "actor-count"           0
                "last-statement-stored" nil
                "platform-frequency"    {}
                "timeline"              []}
               edn-body)))
      (testing "validates params"
        (testing "valid param"
          (let [{:keys [status]}
                (curl/get
                 "http://localhost:8080/admin/status?timeline-unit=minute"
                 {:headers headers
                  :throw   false})]
            ;; success
            (is (= 200 status))))
        (testing "invalid param"
          (let [{:keys [status]}
                (curl/get
                 "http://localhost:8080/admin/status?timeline-unit=foo"
                 {:headers headers
                  :throw   false})]
            ;; failure
            (is (= 400 status))))))
    (testing "manage reactions"
      (let [endpoint     "http://localhost:8080/admin/reaction"
            {:keys [status
                    body]}
            (curl/post endpoint
                       {:headers headers
                        :body
                        (u/write-json-str
                         {:ruleset tc/simple-reaction-ruleset
                          :active  true})})
            reaction-id  (-> (u/parse-json body :keyword-keys? true)
                             :reaction-id
                             u/str->uuid)
            results->edn (fn [reaction-record]
                           (-> reaction-record
                               (select-keys [:id :ruleset :active])
                               (update :ruleset ru/stringify-template)))]
        (testing "create"
          (is (= 200 status))
          (is (uuid? reaction-id)))
        (testing "create invalid params"
          (is-err-code
           (curl/post endpoint
                      {:headers headers
                       :body
                       (u/write-json-str
                        {})})
           400))
        (testing "read"
          (let [{:keys [status body]} (curl/get endpoint
                                                {:headers headers})]
            (is (= 200 status))
            (is (= [{:id      (u/uuid->str reaction-id)
                     :ruleset tc/simple-reaction-ruleset
                     :active  true}]
                   (-> body
                       (u/parse-json :keyword-keys? true :object? false)
                       :reactions
                       (->> (map results->edn)))))))

        (testing "update"
          (let [{:keys [status body]}
                (curl/put endpoint
                          {:headers headers
                           :body
                           (u/write-json-str
                            {:reaction-id (u/uuid->str reaction-id)
                             :active      false})})]
            (is (= 200 status))
            (is (= {:reaction-id (u/uuid->str reaction-id)}
                   (u/parse-json body :keyword-keys? true))))
          (is (= [{:id      (u/uuid->str reaction-id)
                   :ruleset tc/simple-reaction-ruleset
                   :active  false}]
                 (-> (curl/get endpoint
                               {:headers headers})
                     :body
                     (u/parse-json :keyword-keys? true :object? false)
                     :reactions
                     (->> (map results->edn))))))
        (testing "update invalid params"
          (is-err-code
           (curl/put endpoint
                     {:headers headers
                      :body
                      (u/write-json-str
                       {:reaction-id (u/uuid->str reaction-id)
                        :ruleset     {}})})
           400))
        (testing "delete"
          (let [{:keys [status body]}
                (curl/delete endpoint
                             {:headers headers
                              :body
                              (u/write-json-str
                               {:reaction-id (u/uuid->str reaction-id)})})]
            (is (= 200 status))
            (is (= {:reaction-id (u/uuid->str reaction-id)}
                   (u/parse-json body :keyword-keys? true))))
          (is (= {:reactions []}
                 (-> (curl/get endpoint
                               {:headers headers})
                     :body
                     (u/parse-json :keyword-keys? true :object? false)))))))
    (component/stop sys')))

(deftest auth-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        jwt  (-> (curl/post "http://0.0.0.0:8080/admin/account/login"
                            {:headers content-type
                             :body    (u/write-json-str
                                       {"username" "username"
                                        "password" "password"})})
                 :body
                 u/parse-json
                 (get "json-web-token"))
        auth {"Authorization" (str "Bearer " jwt)}
        hdr  (merge content-type auth)]
    (testing "credential creation"
      (let [{:keys [status body]}
            (curl/post "http://0.0.0.0:8080/admin/creds"
                       {:headers hdr
                        :body (u/write-json-str
                               {"scopes" ["all" "all/read"]})})
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
                ["all/read" "statements/read" "statements/read/mine"]
                {:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body    (u/write-json-str {"api-key"    api-key
                                              "secret-key" secret-key
                                              "scopes"     req-scopes})})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{"all/read" "statements/read" "statements/read/mine"}
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
            (is (= #{"all/read" "statements/read" "statements/read/mine"}
                   (set scopes')))))
        (testing "and no-op scope update"
          (let [req-scopes
                ["all/read" "statements/read" "statements/read/mine"]
                {:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body   (u/write-json-str {"api-key"    api-key
                                             "secret-key" secret-key
                                             "scopes"     req-scopes})})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{"all/read" "statements/read" "statements/read/mine"}
                   (set scopes)))))
        (testing "and deleting all scopes"
          (let [{:keys [status body]}
                (curl/put
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body   (u/write-json-str {"api-key"    api-key
                                             "secret-key" secret-key
                                             "scopes"     []})})
                {:strs [scopes]}
                (u/parse-json body)]
            (is (= 200 status))
            (is (= #{} (set scopes)))))
        (testing "and deletion"
          (let [{:keys [status]}
                (curl/delete
                 "http://0.0.0.0:8080/admin/creds"
                 {:headers hdr
                  :body    (u/write-json-str {"api-key"    api-key
                                              "secret-key" secret-key})})]
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
