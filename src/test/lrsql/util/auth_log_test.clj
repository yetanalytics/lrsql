(ns lrsql.util.auth-log-test
  "Tests for the authentication log's line formats, its reason codes for
   OIDC decoder refusals, value cleaning, and that a failure to log never
   changes a response."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [babashka.curl :as curl]
            [buddy.sign.jwt :as bj]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.pedestal-oidc.interceptor :as oidc-i]
            [com.yetanalytics.pedestal-oidc.jwt :as oidc-jwt]
            [lrsql.init.oidc :as init-oidc]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
            [lrsql.util.auth-log :as al]))

;; Only `logger-failure-test` starts a system, so only it uses the database
;; fixture. On PostgreSQL, MariaDB and MySQL that fixture truncates the tables
;; after the test, and they exist only once a system has started.
(use-fixtures :once support/instrumentation-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Line formats
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private uuid "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

(def ^:private formats
  {:login-success   (re-pattern (str "AUTH event=admin\\.login outcome=success account=" uuid " method=local src=\\S+ status=200"))
   :login-failure   #"AUTH event=admin\.login outcome=failure user=\"[\x20-\x7E&&[^\"\\]]{1,64}\" reason=invalid-credentials src=\S+ status=401"
   :renew-success   (re-pattern (str "AUTH event=admin\\.renew outcome=success account=" uuid " session=[0-9a-f]{8} src=\\S+ status=200"))
   :renew-failure   (re-pattern (str "AUTH event=admin\\.renew outcome=failure account=" uuid " reason=refresh-expired src=\\S+ status=401"))
   :logout-success  (re-pattern (str "AUTH event=admin\\.logout outcome=success account=" uuid " session=[0-9a-f]{8} src=\\S+ status=200"))
   :request-refused #"AUTH event=admin\.request outcome=refused route=\S+ reason=(invalid-token|expired|blocked|session-ended|account-missing|issuer-mismatch|oidc-claims|oidc-scope|oidc-required|keyset-error) src=\S+ status=401"
   :request-success #"AUTH event=admin\.request outcome=success route=\S+ account=(\S+|\"[^\"]*\")( issuer=\"[^\"]*\")? method=(local|oidc|onetime) src=\S+ status=2\d\d"
   :xapi-refused    #"AUTH event=xapi\.request outcome=refused reason=(no-credentials|invalid-credentials|scope) credential=(\S+) src=\S+ status=(401|403)"})

(defn- matches? [fmt line] (boolean (re-matches (formats fmt) line)))

(defn- ctx-of
  [route-name path status & {:as more}]
  (merge {:route    {:route-name route-name}
          :request  {:path-info path :remote-addr "10.1.2.3"}
          :response {:status status}}
         more))

(def ^:private acct #uuid "00000000-0000-4000-8000-000000000001")

(deftest event-line-test
  (testing "each event in its fixed format"
    (is (matches? :login-success
                  (second (al/event-line
                           (ctx-of :lrsql.admin.account/login "/admin/account/login" 200
                                   :lrsql.admin.interceptors.account/data
                                   {:username "u" :password "p" :account-id acct})))))
    (is (matches? :login-failure
                  (second (al/event-line
                           (ctx-of :lrsql.admin.account/login "/admin/account/login" 401
                                   :lrsql.admin.interceptors.account/data
                                   {:username "someone" :password "p"}
                                   ::al/reason :invalid-credentials)))))
    (is (matches? :renew-success
                  (second (al/event-line
                           (ctx-of :lrsql.admin.account/renew "/admin/account/renew" 200
                                   :lrsql.admin.interceptors.jwt/data
                                   {:account-id acct :session-id "AAAAAAAAAAAAAAAAAAAAAA"})))))
    (is (matches? :renew-failure
                  (second (al/event-line
                           (ctx-of :lrsql.admin.account/renew "/admin/account/renew" 401
                                   :lrsql.admin.interceptors.jwt/data {:account-id acct}
                                   ::al/reason :refresh-expired)))))
    (is (matches? :logout-success
                  (second (al/event-line
                           (ctx-of :lrsql.admin.account/logout "/admin/account/logout" 200
                                   :lrsql.admin.interceptors.jwt/data
                                   {:account-id acct :session-id "AAAAAAAAAAAAAAAAAAAAAA"})))))
    (doseq [reason [:invalid-token :expired :blocked :session-ended :account-missing
                    :issuer-mismatch :oidc-claims :oidc-scope :oidc-required :keyset-error]]
      (let [line (second (al/event-line (ctx-of :lrsql.admin.me/get "/admin/me" 401
                                                ::al/reason reason)))]
        (is (matches? :request-refused line))
        (is (cstr/includes? line (str "reason=" (name reason))))))
    (is (matches? :request-success
                  (second (al/event-line
                           (ctx-of :lrsql.admin.me/get "/admin/me" 200
                                   :lrsql.admin.interceptors.jwt/data {:account-id acct})))))
    (is (matches? :xapi-refused
                  (second (al/event-line (ctx-of :x/statements "/xapi/statements" 401))))))
  (testing "levels: INFO for successes, WARN for refusals"
    (is (= :info (first (al/event-line (ctx-of :lrsql.admin.me/get "/admin/me" 200
                                               :lrsql.admin.interceptors.jwt/data {:account-id acct})))))
    (is (= :warn (first (al/event-line (ctx-of :lrsql.admin.me/get "/admin/me" 401))))))
  (testing "OIDC: the username and issuer are separate keys, each cleaned"
    (let [line (second (al/event-line
                        (ctx-of :lrsql.admin.me/get "/admin/me" 200
                                :lrsql.admin.interceptors.jwt/data {:account-id acct}
                                :lrsql.admin.interceptors.oidc/admin-identity
                                {:username    "alice\u0007\"x"
                                 :oidc-issuer "https://idp.example/realms/r\u0000\""
                                 :scopes      #{:scope/admin}})))]
      (is (matches? :request-success line))
      (is (cstr/includes?
           line
           "account=\"alice_x\" issuer=\"https://idp.example/realms/r_\" method=oidc"))
      (is (not (cstr/includes? line "@")))))
  (testing "OIDC: a long username keeps the issuer key intact"
    (let [line (second (al/event-line
                        (ctx-of :lrsql.admin.me/get "/admin/me" 200
                                :lrsql.admin.interceptors.oidc/admin-identity
                                {:username    (apply str (repeat 100 "a b"))
                                 :oidc-issuer "https://idp.example/realms/r"})))
          [_ user issuer] (re-find #"account=\"([^\"]*)\" issuer=\"([^\"]*)\"" line)]
      (is (matches? :request-success line))
      (is (= 64 (count user)))
      (is (= "https://idp.example/realms/r" issuer))))
  (testing "local accounts carry no issuer key"
    (let [line (second (al/event-line
                        (ctx-of :lrsql.admin.me/get "/admin/me" 200
                                :lrsql.admin.interceptors.jwt/data {:account-id acct})))]
      (is (cstr/includes? line (str "account=" acct " method=local")))
      (is (not (cstr/includes? line "issuer=")))))
  (testing "a refused unknown reason is reported as invalid-token"
    (is (cstr/includes? (second (al/event-line (ctx-of :lrsql.admin.me/get "/admin/me" 401
                                                       ::al/reason :something-else)))
                        "reason=invalid-token")))
  (testing "xAPI: scope refusals carry the credential id, never the key or secret"
    (let [line (second (al/event-line
                        (ctx-of :x/statements "/xapi/statements" 403
                                :com.yetanalytics.lrs.auth/identity
                                {:auth   {:basic {:username "the-api-key" :password "the-api-secret"}}
                                 :agent  {"objectType" "Agent"
                                          "account" {"homePage" "http://example.org"
                                                     "name" (str acct)}}
                                 :scopes #{:scope/statements.read}})))]
      (is (matches? :xapi-refused line))
      (is (cstr/includes? line (str "reason=scope credential=" acct)))
      (is (not (cstr/includes? line "the-api-key")))
      (is (not (cstr/includes? line "the-api-secret")))))
  (testing "xAPI: credentials present but refused, and none at all"
    (is (cstr/includes? (second (al/event-line
                                 (ctx-of :x/statements "/xapi/statements" 401
                                         :request {:path-info "/xapi/statements"
                                                   :remote-addr "10.1.2.3"
                                                   :headers {"authorization" "Basic Zm9vOmJhcg=="}})))
                        "reason=invalid-credentials credential=-"))
    (is (cstr/includes? (second (al/event-line (ctx-of :x/statements "/xapi/statements" 401)))
                        "reason=no-credentials")))
  (testing "nothing for successful xAPI requests or unauthenticated admin pages"
    (is (nil? (al/event-line (ctx-of :x/statements "/xapi/statements" 200))))
    (is (nil? (al/event-line (ctx-of :lrsql.admin.ui/env "/admin/env" 200))))
    (is (nil? (al/event-line (ctx-of :health "/health" 200))))))

(deftest keyset-error-reason-test
  (testing "a request refused because the key set cannot be fetched logs keyset-error"
    ;; pedestal-oidc's decoder with a key lookup that fetches the key set, as
    ;; lrsql's does; nothing listens on port 1, as when the identity provider
    ;; is down. The token is a local admin token, which carries no key id.
    (let [decode (oidc-i/decode-interceptor
                  (fn [_] (fn [kid] (get (oidc-jwt/get-keyset "http://127.0.0.1:1/certs") kid)))
                  :required? false
                  :unauthorized init-oidc/decoder-unauthorized)
          now    (quot (System/currentTimeMillis) 1000)
          token  (bj/sign {:acc (str acct) :iat now :exp (+ now 600)}
                          "a-test-key-of-more-than-thirty-two-bytes")
          ctx    {:route   {:route-name :lrsql.admin.me/get}
                  :request {:path-info   "/admin/me"
                            :remote-addr "10.1.2.3"
                            :headers     {"authorization" (str "Bearer " token)}}}
          out    ((:enter decode) ctx)
          line   (second (al/event-line out))]
      (is (= 401 (get-in out [:response :status])))
      (is (= :keyset-error (::al/reason out)))
      (is (matches? :request-refused line))
      (is (cstr/includes? line "reason=keyset-error"))))
  (testing "other decoder refusals keep invalid-token"
    (is (= :invalid-token (init-oidc/decoder-refusal-reason
                           :token-invalid (ex-info "bad" {:type :validation}))))
    (is (= :invalid-token (init-oidc/decoder-refusal-reason
                           :unknown (ex-info "other" {:type ::other}))))
    (is (= :invalid-token (init-oidc/decoder-refusal-reason :header-invalid nil))))
  (testing "pedestal-oidc's own key set failures map to keyset-error"
    (is (= :keyset-error (init-oidc/decoder-refusal-reason :keyset-error nil)))
    (is (= :keyset-error (init-oidc/decoder-refusal-reason :keyset-invalid nil)))))

(deftest clean-test
  (testing "a 200-character value with control characters becomes 64 printable ASCII characters"
    (let [raw (apply str (take 200 (cycle "ab\u0000c\td\u001b[0me\"f\\g\u00e9\u65e5")))
          out (al/clean raw)]
      (is (= 64 (count out)))
      (is (re-matches #"[\x20-\x7E]{64}" out))
      (is (not-any? #{\" \\} out))))
  (testing "nil and an all-control value become -"
    (is (= "-" (al/clean nil)))
    (is (= "-" (al/clean "\u0000\u0001")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logging never changes a response
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- rand-hex [n]
  (apply str (repeatedly n #(rand-nth "0123456789abcdef"))))

;; Generated per run; never printed.
(def ^:private planted
  {:admin-pass (str "Pw-" (rand-hex 24) "!")
   :jwt-secret (str "Sk-" (rand-hex 40))})

(def ^:private base "http://0.0.0.0:8080")
(def ^:private json {"Content-Type" "application/json"})

(defn- bearer [jwt] (merge json {"Authorization" (str "Bearer " jwt)}))

(defn- req [method path opts]
  ((case method :get curl/get :post curl/post)
   (str base path) (merge {:throw false} opts)))

(defn- start []
  (component/start
   (support/test-system
    :conf-overrides {[:lrs :admin-user-default]      "authlogadmin"
                     [:lrs :admin-pass-default]      (:admin-pass planted)
                     [:webserver :jwt-common-secret] (:jwt-secret planted)})))

(defn- login-body [user pass]
  (u/write-json-str {"username" user "password" pass}))

(defn- token-of [resp] (-> resp :body u/parse-json (get "json-web-token")))

(defn- logger-failure-check []
  (let [sys (start)]
    (try
      (testing "a logger that throws changes neither status nor body"
        (let [good (login-body "authlogadmin" (:admin-pass planted))
              ok   (req :post "/admin/account/login" {:headers json :body good})
              bad  (with-redefs-fn {#'al/emit! (fn [_] (throw (RuntimeException. "log down")))}
                     #(req :post "/admin/account/login" {:headers json :body good}))
              bad2 (with-redefs-fn {#'al/event-line (fn [_] (throw (RuntimeException. "log down")))}
                     #(req :get "/admin/me" {:headers (bearer (token-of ok))}))]
          (is (= 200 (:status ok) (:status bad) (:status bad2)))
          (is (= (keys (u/parse-json (:body ok))) (keys (u/parse-json (:body bad)))))
          (is (string? (token-of bad)))
          (is (= 401 (:status (with-redefs-fn {#'al/emit! (fn [_] (throw (Error. "log down")))}
                                #(req :get "/admin/me" {:headers (bearer "nope")})))))))
      (finally
        (component/stop sys)))))

(deftest logger-failure-test
  (support/fresh-db-fixture logger-failure-check))
