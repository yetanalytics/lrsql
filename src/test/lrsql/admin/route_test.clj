(ns lrsql.admin.route-test
  "Test for admin-related interceptors + routes
   (as opposed to just the protocol)."
  (:require [clojure.test :refer [deftest testing is use-fixtures are]]
            [clojure.string :refer [lower-case]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [xapi-schema.spec.regex :refer [Base64RegEx]]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.backend.protocol :as bp]
            [lrsql.test-support :as support]
            [lrsql.lrs-test :as lt]
            [lrsql.test-constants :as tc]
            [lrsql.util :as u]
            [lrsql.util.headers :as h]
            [lrsql.util.reaction :as ru]
            [lrsql.util.actor :as ua]
            [next.jdbc :as jdbc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def auth-ident {:agent  {"objectType" "Agent"
                          "account"    {"homePage" "http://example.org"
                                        "name"     "12341234-0000-4000-1234-123412341234"}}
                 :scopes #{:scope/all}})

(def stmt-0 {"id"      "00000000-0000-4000-8000-000000000000"
             "actor"   {"mbox"       "mailto:sample.foo@example.com"
                        "objectType" "Agent"}
             "verb"    {"id"      "http://adlnet.gov/expapi/verbs/answered"
                        "display" {"en-US" "answered"
                                   "zh-CN" "回答了"}}
             "object"  {"id" "http://www.example.com/tincan/activities/multipart"}
             "context" {"platform" "example"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def content-type {"Content-Type" "application/json"})

(defn- login-account
  [headers body]
  (curl/post "http://0.0.0.0:8080/admin/account/login"
             {:headers headers
              :body    body}))

(defn- logout-account
  [headers]
  (curl/post "http://0.0.0.0:8080/admin/account/logout"
             {:headers headers}))

(defn- renew-login
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/account/renew"
            {:headers headers}))

(defn- create-account
  [headers body & {:keys [throw]
                   :or {throw true}}]
  (curl/post "http://0.0.0.0:8080/admin/account/create"
             {:headers headers
              :body    body
              :throw   throw}))

(defn- get-env
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/env"
            {:headers headers}))

(defn- get-account
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/account"
            {:headers headers}))

(defn- get-me
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/me"
            {:headers headers}))

(defn- verify-me
  [headers]
  (curl/get "http://0.0.0.0:8080/admin/verify"
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

(defn- delete-actor
  [headers body]
  (curl/delete "http://0.0.0.0:8080/admin/agents" {:headers headers
                                                   :body body}))

(defn- get-statements-via-url-param [headers credential-id]
  (curl/get (str "http://0.0.0.0:8080/xapi/statements?credentialID=" credential-id)
            {:headers (merge headers
                             {"Accept" "application/json"
                              "X-Experience-API-Version" "1.0.3"})}))

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

(def sec-header-names (mapv #(lower-case (% h/sec-head-names))
                            (keys h/sec-head-names)))

(deftest admin-routes-test
  (let [sys       (support/test-system)
        sys'      (component/start sys)
        ;; Seed information
        {:keys [admin-user-default
                admin-pass-default]}
        (get-in sys' [:lrs :config])
        seed-body (u/write-json-str
                   {"username" admin-user-default
                    "password" admin-pass-default})
        seed-jwt  (-> (login-account content-type seed-body)
                      :body
                      u/parse-json
                      (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        ;; New data information
        headers   (merge content-type seed-auth)
        orig-pass "Swordfish100?"
        req-body  (u/write-json-str {"username" "mylongname"
                                     "password" orig-pass})
        ;; Corrupted JWT
        bad-jwt   (apply str (butlast seed-jwt))
        bad-auth  {"Authorization" (str "Bearer " bad-jwt)}
        bad-head  (merge content-type bad-auth)]
    (try
      (testing "seed jwt retrieved"
        ;; Sanity check that the test credentials are in place
        (is (some? seed-jwt)))
      (testing "got environment vars"
        (let [{:keys [status body]} (get-env content-type)
              edn-body              (u/parse-json body)]
          (is (= 200 status))
          (is (= (get edn-body "url-prefix") "/xapi"))))
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
      (testing "create accounts with invalid username and passwords"
        (are [input expected-status]
             (let [{:keys [status]} (create-account
                                     headers
                                     (u/write-json-str
                                      input)
                                     :throw false)]
               (= expected-status status))
          ;; both empty
          {"username" ""
           "password" ""}                 400
          ;; empty username valid pass
          {"username" ""
           "password" "_P4ssW0rd!"}       400
          ;; specials in username valid pass
          {"username" "bobdobbs!"
           "password" "_P4ssW0rd!"}       400
          {"username" "bob dobbs"
           "password" "_P4ssW0rd!"}       400
          {"username" "<script>alert('xss')</script>"
           "password" "_P4ssW0rd!"}       400
          ;; empty password
          {"username" "bobdobbs"
           "password" ""}                 400
          ;; short password
          {"username" "bobdobbs"
           "password" "password"}         400
          ;; only alpha
          {"username" "bobdobbs"
           "password" "passwordpassword"} 400
          ;; only alphanum
          {"username" "bobdobbs"
           "password" "p4sswordp4ssword"} 400
          ;; only alphanum + caps
          {"username" "bobdobbs"
           "password" "P4sswordP4ssword"} 400
          ;; valid
          {"username" "bobdobbs"
           "password" "_P4ssW0rd!"}       200))
      (testing "get all admin accounts"
        (let [{:keys [status
                      body]} (get-account headers)
              edn-body       (u/parse-json body :object? false)]
          ;; success
          (is (= 200 status))
          ;; is a vec
          (is (vector? edn-body))
          ;; has the created user
          (is (some #(= (get % "username") "mylongname") edn-body))))
      (testing "get my admin account"
        (let [{:keys [status
                      body]} (get-me headers)
              edn-body       (u/parse-json body)]
          ;; success
          (is (= 200 status))
          ;; is the created user
          (is (= (get edn-body "username") admin-user-default))))
      (testing "verify my admin account"
        (let [{:keys [status]} (verify-me headers)]
                ;; success
          (is (= 204 status))))
      (testing "ensure corrupted JWTs do not pass"
        (is-err-code (get-me bad-head) 401)
        (is-err-code (verify-me bad-head) 401))
      (testing "renew my admin account's JWT"
        ;; NOTE: More JWT renewal tests in `jwt-expiry` below
        (let [{:keys [status body]} (renew-login headers)
              edn-body (u/parse-json body)
              new-jwt  (get edn-body "json-web-token")]
          ;; success
          (is (= 200 status))
          ;; body is a new JWT
          (is (string? new-jwt))
          (is (not= seed-jwt new-jwt))))
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
              new-pass     "Fn0rdfish!"
              update-pass! (fn [payload]
                             (update-account-password
                              update-head
                              (u/write-json-str payload)))]
          (is (-> (update-pass! {"old-password" orig-pass
                                 "new-password" new-pass})
                  :status
                  (= 200)))
          (is (-> (login-account
                   content-type
                   (u/write-json-str {"username" "mylongname"
                                      "password" new-pass}))
                  :status
                  (= 200)))
          (testing "with the wrong password"
            (is (-> (update-pass! {"old-password" "verywrongpass"
                                   "new-password" "Wh0c4r3s?!?"})
                    :status
                    (= 401))))
          (testing "without a change"
            (is (-> (update-pass! {"old-password" new-pass
                                   "new-password" new-pass})
                    :status
                    (= 400))))
          (testing "with an invalid password"
            (are [password expected-status]
                 (-> (update-pass! {"old-password" new-pass
                                    "new-password" password})
                     :status
                     (= expected-status))
              ""         400
              "password" 400))
          (testing "change it back"
            (is (-> (update-pass! {"old-password" new-pass
                                   "new-password" orig-pass})
                    :status
                    (= 200))))))
      (testing "authenticate and download CSV data"
        ;; TODO: Add tests with statements and applicable query params
        (let [property-paths-vec [["id"] ["actor" "mbox"]]
              property-paths-str (u/url-encode (str property-paths-vec))
              bad-prop-paths-vec ["zoo" "wee" "mama"]
              bad-prop-paths-str (u/url-encode (str bad-prop-paths-vec))
              auth-endpoint-url  "http://0.0.0.0:8080/admin/csv/auth"
              agent-url-encoded  "%7B%22name%22%3A%22Fred+Ersatz%22%2C%22mbox%22%3A%22mailto%3Afrederstaz@example.org%22%7D"
              {:keys [status body]}
              (curl/get auth-endpoint-url {:headers headers})
              {:keys [account-id json-web-token]}
              (u/parse-json body :keyword-keys? true)]
          (is (= 200 status))
          (is (string? account-id))
          (is (string? json-web-token))
          (let [endpoint-url
                (format "http://0.0.0.0:8080/admin/csv?token=%s&property-paths=%s&ascending=true&agent=%s"
                        json-web-token
                        property-paths-str
                        agent-url-encoded)
                bad-endpoint-url-1
                (format "http://0.0.0.0:8080/admin/csv?token=%s&property-paths=%s"
                        seed-jwt
                        property-paths-str)
                bad-endpoint-url-2
                (format "http://0.0.0.0:8080/admin/csv?token=%s&property-paths=%s"
                        json-web-token
                        bad-prop-paths-str)]
            (testing "- valid download"
              (let [{:keys [status body]}
                    (curl/get endpoint-url {:headers headers :as :stream})
                    csv-body (slurp body)]
                (is (= 200 status))
                (is (= "id,actor_mbox\r\n" csv-body))))
            (testing "- expired one-time token does not authenticate"
              (is-err-code (curl/get endpoint-url {:headers headers :as :stream})
                           401))
            (testing "- account JWT does not authenticate"
              (is-err-code (curl/get bad-endpoint-url-1 {:headers headers :as :stream})
                           401))
            (testing "- invalid property path"
              (is-err-code (curl/get bad-endpoint-url-2 {:headers headers :as :stream})
                           400)))))
      (testing "delete the `myname` account using the seed account"
        (let [del-jwt  (-> (login-account content-type req-body)
                           :body
                           u/parse-json
                           (get "json-web-token"))
              del-id   (-> (get-account headers)
                           :body
                           (u/parse-json :object? false)
                           (#(filter (fn [acc]
                                       (= (get acc "username") "mylongname"))
                                     %))
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
                           {:title   "reaction-0"
                            :ruleset tc/simple-reaction-ruleset
                            :active  true})})
              reaction-id  (-> (u/parse-json body :keyword-keys? true)
                               :reactionId
                               u/str->uuid)
              results->edn (fn [reaction-record]
                             (-> reaction-record
                                 (select-keys [:id :title :ruleset :active])
                                 (update :ruleset ru/json->ruleset)))]
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
                       :title   "reaction-0"
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
                              {:reactionId (u/uuid->str reaction-id)
                               :active     false})})]
              (is (= 200 status))
              (is (= {:reactionId (u/uuid->str reaction-id)}
                     (u/parse-json body :keyword-keys? true))))
            (is (= [{:id      (u/uuid->str reaction-id)
                     :title   "reaction-0"
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
                         {:reactionId (u/uuid->str reaction-id)
                          :ruleset    {}})})
             400))
          (testing "delete"
            (let [{:keys [status body]}
                  (curl/delete endpoint
                               {:headers headers
                                :body
                                (u/write-json-str
                                 {:reactionId (u/uuid->str reaction-id)})})]
              (is (= 200 status))
              (is (= {:reactionId (u/uuid->str reaction-id)}
                     (u/parse-json body :keyword-keys? true))))
            (is (= {:reactions []}
                   (-> (curl/get endpoint
                                 {:headers headers})
                       :body
                       (u/parse-json :keyword-keys? true :object? false)))))))
      (testing "omitted sec headers because not configured"
        (let [{:keys [headers]} (get-env content-type)]
          (is (empty? (select-keys headers sec-header-names)))))
      (testing "delete actor route"
        (let [lrs (:lrs sys')

              ifi         (ua/actor->ifi (stmt-0 "actor"))
              count-by-id (fn [id]
                            (-> (lrsp/-get-statements lrs auth-ident {:statementsId id} [])
                                :statement-result :statements count))]
          (lrsp/-store-statements lrs auth-ident [stmt-0] [])
          (is (= 1 (count-by-id (stmt-0 "id"))))
          (delete-actor headers
                        (u/write-json-str  {"actor-ifi" ifi}))
          (is (zero? (count-by-id (stmt-0 "id"))))))
      ;; Need to do this last since this overrides `headers`
      (testing "log out then log back into the admin account"
        ;; Logout
        (let [{:keys [status]} (logout-account headers)]
          (is (= 200 status)))
        (is-err-code (get-me headers) 401)
        ;; Logout again, should be unauthenticated
        (is-err-code (logout-account headers) 401)
        ;; Login
        (let [{:keys [status body]} (login-account content-type seed-body)
              new-jwt  (-> body
                           u/parse-json
                           (get "json-web-token"))
              new-auth {"Authorization" (str "Bearer " new-jwt)}
              headers  (merge content-type new-auth)]
          (is (= 200 status))
          (is (= 200 (:status (get-me headers))))))
      (finally
        (component/stop sys')))))

(def custom-sec-header-config
  {:sec-head-hsts         h/default-value
   :sec-head-frame        "Chocolate"
   :sec-head-content-type h/default-value
   :sec-head-xss          "Banana"
   :sec-head-download     h/default-value
   :sec-head-cross-domain "Pancakes"
   :sec-head-content      h/default-value})

(def custom-sec-header-expected
  (reduce-kv
   (fn [hdrs k v]
     (assoc hdrs (lower-case (k h/sec-head-names))
            (if (= v h/default-value)
              (k h/sec-head-defaults)
              v)))
   {} custom-sec-header-config))

(deftest custom-header-admin-routes
  (let [hdr-conf (reduce-kv (fn [m k v] (assoc m [:webserver k] v))
                            {} custom-sec-header-config)
        sys  (support/test-system
              :conf-overrides hdr-conf)
        sys' (component/start sys)]
    (try
      (testing "Custom Sec Headers"
        ;; Run a basic admin routes call and verify success
        (let [{:keys [headers]} (get-env content-type)]
          ;; equals the same combination of custom and default hdr values
          (is (= custom-sec-header-expected
                 (select-keys headers sec-header-names)))))
      (finally
        (component/stop sys')))))

(deftest jwt-expiry
  (let [sys  (support/test-system
              :conf-overrides
              {[:webserver :jwt-exp-time] 3
               [:webserver :jwt-refresh-exp-time] 4
               [:webserver :jwt-refresh-interval] 2
               [:webserver :jwt-interaction-window] 2})
        sys' (component/start sys)
        ;; Seed info
        {:keys [admin-user-default
                admin-pass-default]}
        (get-in sys' [:lrs :config])
        seed-body (u/write-json-str
                   {"username" admin-user-default
                    "password" admin-pass-default})
        seed-jwt  (-> (login-account content-type seed-body)
                      :body
                      u/parse-json
                      (get "json-web-token"))
        seed-auth {"Authorization" (str "Bearer " seed-jwt)}
        headers   (merge content-type seed-auth)]
    (try
      (testing "Original JWT works"
        (is (= 200 (:status (get-me headers)))))
      ;; Refresh JWT
      (Thread/sleep 2000)
      (let [{:keys [body]} (renew-login headers)
            new-jwt  (-> body u/parse-json (get "json-web-token"))
            new-auth {"Authorization" (str "Bearer " new-jwt)}
            headers* (merge content-type new-auth)]
        (testing "Only new JWT no longer works after expiration"
          (Thread/sleep 2000)
          (is-err-code (get-me headers) 401)
          (is (= 200 (:status (get-me headers*)))))
        (testing "JWT can no longer refresh after refresh expiration"
          (Thread/sleep 1000)
          (is-err-code (renew-login headers) 401))
        (testing "JWT no longer works after expiration"
          (Thread/sleep 1500)
          (is-err-code (get-me headers*) 401)))
      (finally
        (component/stop sys')))))

(def proxy-jwt-body
  {"usercertificate" "unique.user.1234"
   "iss"             "https://idp.domain.com/auth"
   "group-full"      ["/domain/app/ADMIN"]})

(defn- proxy-jwt
  "Takes an edn claims body and returns a (not properly signed!) JWT
   for proxy jwt testing"
  [body]
  (str "eyJhbGciOiJIUzI1NiJ9."
       (u/str->base64encoded-str (u/write-json-str body))
       ".GLkxNsaxkZQiW4voy4RKEpLy8RxyzcpMBAeBw-aIykQ"))

(deftest proxy-jwt-admin-routes
  (let [sys  (support/test-system
              :conf-overrides
              {[:webserver :jwt-no-val]          true
               [:webserver :jwt-no-val-uname]    "usercertificate"
               [:webserver :jwt-no-val-issuer]   "iss"
               [:webserver :jwt-no-val-role-key] "group-full"
               [:webserver :jwt-no-val-role]     "/domain/app/ADMIN"})
        sys' (component/start sys)
        lrs (:lrs sys')
        ds (get-in sys' [:lrs :connection :conn-pool])
        backend (:backend sys')
        ;; proxy jwt auth
        proxy-auth {"Authorization" (str "Bearer " (proxy-jwt proxy-jwt-body))}
        headers    (merge content-type proxy-auth)]
    (try
      (testing "Proxy JWT authentication"
        ;; Run a basic admin routes call and verify success
        (let [{:keys [status body]} (get-account headers)
              edn-body (u/parse-json body :object? false)]
          ;; 200 response
          (is (= status 200))
          ;; not only is there a body but it should now contain our jwt user
          (is (some #(= (get % "username")
                        (get proxy-jwt-body "usercertificate"))
                    edn-body))
          ;; get-me route still works
          (is (= 200 (:status (get-me headers))))
          ;; logout fails in this mode
          (is-err-code (logout-account headers) 400)))
      (testing "Bad Proxy JWT role"
        ;; Remove the matching role from jwt role-key field and rerun admin call
        (let [bad-jwt-bdy (assoc proxy-jwt-body "group-full" ["NOTADMIN"])
              bad-auth {"Authorization" (str "Bearer " (proxy-jwt bad-jwt-bdy))}
              bad-headers (merge content-type bad-auth)]
          ;; Bad Auth because nonmatching role
          (is-err-code (get-account bad-headers) 401)))

      (testing "/statements route for admin when proxy JWT"
        (let [{:keys [status body]}
              (curl/post "http://0.0.0.0:8080/admin/creds"
                         {:headers headers
                          :body (u/write-json-str
                                 {"scopes" ["all" "all/read"]})})
              {:strs [api-key secret-key scopes]}
              (u/parse-json body)

              {credential-id :cred_id}
              (jdbc/with-transaction [tx ds]
                (bp/-query-credential-ids backend tx {:api-key api-key
                                                      :secret-key secret-key}))

              _ (lrsp/-store-statements lrs auth-ident [lt/stmt-0] [])
              {:keys [status body]} (get-statements-via-url-param headers credential-id)]
          (is (= status 200))
          (is (not (empty? ((u/parse-json body) "statements"))))))
      
      (finally
        (component/stop sys')))))

(deftest auth-routes-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        backend (:backend sys')
        ds (get-in sys' [:lrs :connection :conn-pool])
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
    (try
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
            (let [{seed-credential-id :cred_id}
                  (jdbc/with-transaction [tx ds]
                    (bp/-query-credential-ids backend tx {:api-key "username"
                                                          :secret-key "password"}))
                  {new-credential-id :cred_id}
                  (jdbc/with-transaction [tx ds]
                    (bp/-query-credential-ids backend tx {:api-key api-key
                                                          :secret-key secret-key}))
                  
                  {:keys [status body]}
                  (curl/get
                   "http://0.0.0.0:8080/admin/creds"
                   {:headers hdr})
                  body* (u/parse-json body :object? false)]
              (is (= 200 status))
              ;; Seed cred
              (is (= {"api-key"    "username"
                      "secret-key" "password"
                      "label"      nil
                      "scopes"     ["all"]
                      "seed?"      true
                      "id" (str seed-credential-id)}
                     (first (filter (fn [cred]
                                      (= "username" (get cred "api-key")))
                                    body*))))
              ;; New cred
              (is (= {"api-key"    api-key
                      "secret-key" secret-key
                      "label"      nil
                      "scopes"     scopes
                      "id"         (str new-credential-id)}
                     (first (filter (fn [cred]
                                      (= api-key (get cred "api-key")))
                                    body*))))))
          
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
          (testing "and no-op label + scope update"
            (let [req-scopes
                  ["all/read" "statements/read" "statements/read/mine"]
                  {:keys [status body]}
                  (curl/put
                   "http://0.0.0.0:8080/admin/creds"
                   {:headers hdr
                    :body   (u/write-json-str {"api-key"    api-key
                                               "secret-key" secret-key
                                               "label"      "My Label"
                                               "scopes"     req-scopes})})
                  {:strs [label scopes]}
                  (u/parse-json body)]
              (is (= 200 status))
              (is (= "My Label" label))
              (is (= #{"all/read" "statements/read" "statements/read/mine"}
                     (set scopes)))))
          (testing "and deleting label and all scopes"
            (let [{:keys [status body]}
                  (curl/put
                   "http://0.0.0.0:8080/admin/creds"
                   {:headers hdr
                            ;; Not including label key = nil label
                    :body   (u/write-json-str {"api-key"    api-key
                                               "secret-key" secret-key
                                               "scopes"     []})})
                  {:strs [label scopes]}
                  (u/parse-json body)]
              (is (= 200 status))
              (is (= nil label))
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

      (finally
        (component/stop sys')))))
