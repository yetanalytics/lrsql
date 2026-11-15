(ns lrsql.admin.auth-event-test
  "Tests that sign-in, renewal, sign-out and refused admin and xAPI requests
   are logged to the lrsql.auth logger, one fixed-format line per event, and
   that no password, API key or secret, signing secret or token reaches it."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
            [lrsql.util.admin :as ua])
  (:import [ch.qos.logback.classic Level Logger]
           [ch.qos.logback.classic.spi ILoggingEvent]
           [ch.qos.logback.core.read ListAppender]
           [java.util Base64]
           [org.slf4j LoggerFactory]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(def ^:private logger-name "lrsql.auth")

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
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Capture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- capture
  "Run `f` with a ListAppender on the lrsql.auth logger at INFO. Returns
   [result lines], each line [level message]."
  [f]
  (let [^Logger lg (LoggerFactory/getLogger ^String logger-name)
        app        (doto (ListAppender.) (.start))
        old        (.getLevel lg)]
    (.setLevel lg Level/INFO)
    (.addAppender lg app)
    (try
      (let [result (f)]
        [result
         (mapv (fn [^ILoggingEvent e]
                 [(keyword (cstr/lower-case (str (.getLevel e))))
                  (.getFormattedMessage e)])
               (vec (.list app)))])
      (finally
        (.detachAppender lg app)
        (.setLevel lg old)))))

(defn- lines-of [captured] (map second (second captured)))

(def ^:private jwt-shaped #"eyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]*\.[A-Za-z0-9_-]*")

(defn- leaks
  "The planted values (and any JWT-shaped string) found in `lines`."
  [lines planted]
  (set (concat (for [l lines p planted :when (cstr/includes? l p)] p)
               (for [l lines :when (re-find jwt-shaped l)] :jwt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- rand-hex [n]
  (apply str (repeatedly n #(rand-nth "0123456789abcdef"))))

;; Generated per run; never printed.
(def ^:private planted
  {:admin-pass (str "Pw-" (rand-hex 24) "!")
   :api-key    (rand-hex 64)
   :api-secret (rand-hex 64)
   :jwt-secret (str "Sk-" (rand-hex 40))})

(def ^:private base "http://0.0.0.0:8080")
(def ^:private json {"Content-Type" "application/json"})

(defn- bearer [jwt] (merge json {"Authorization" (str "Bearer " jwt)}))

(defn- basic [k s]
  {"Authorization" (str "Basic " (.encodeToString (Base64/getEncoder)
                                                  (.getBytes (str k ":" s) "UTF-8")))
   "X-Experience-API-Version" "1.0.3"
   "Content-Type" "application/json"})

(defn- req [method path opts]
  ((case method :get curl/get :post curl/post :delete curl/delete)
   (str base path) (merge {:throw false} opts)))

(defn- start []
  (component/start
   (support/test-system
    :conf-overrides {[:lrs :admin-user-default]       "authlogadmin"
                     [:lrs :admin-pass-default]       (:admin-pass planted)
                     [:lrs :api-key-default]          (:api-key planted)
                     [:lrs :api-secret-default]       (:api-secret planted)
                     [:webserver :jwt-common-secret]  (:jwt-secret planted)})))

(defn- login-body [user pass]
  (u/write-json-str {"username" user "password" pass}))

(defn- token-of [resp] (-> resp :body u/parse-json (get "json-web-token")))

(defn- claims [jwt]
  (-> (re-find #"^[^.]+\.([^.]+)\." jwt) second
      (->> (.decode (Base64/getUrlDecoder))) u/parse-json))

(def ^:private stmt
  (u/write-json-str {"actor"  {"mbox" "mailto:authlog@example.org" "objectType" "Agent"}
                     "verb"   {"id" "http://adlnet.gov/expapi/verbs/answered"}
                     "object" {"id" "http://www.example.com/authlog"}}))

(defn- exercise!
  "Every event the routes produce, in one system. Returns a map of the
   statuses seen, for the response assertions."
  []
  (let [good   (login-body "authlogadmin" (:admin-pass planted))
        t0     (token-of (req :post "/admin/account/login" {:headers json :body good}))
        acc    (u/str->uuid (get (claims t0) "acc"))
        sid    (get (claims t0) "sid")
        st     (atom {})
        note!  (fn [k resp] (swap! st assoc k (:status resp)) resp)]
    (note! :login-failure
           (req :post "/admin/account/login"
                {:headers json :body (login-body "authlogadmin" "wrong-password")}))
    (note! :me (req :get "/admin/me" {:headers (bearer t0)}))
    ;; Admin token claims are in whole seconds, so a renewal in the same second
    ;; as the sign-in gives a token identical to t0, and signing out with it
    ;; would block t0 directly instead of through its session.
    (Thread/sleep 1100)
    (let [t1 (token-of (note! :renew (req :get "/admin/account/renew" {:headers (bearer t0)})))]
      ;; refresh limit already passed
      (note! :renew-expired
             (req :get "/admin/account/renew"
                  {:headers (bearer (ua/account-id->jwt*
                                     acc (:jwt-secret planted) 600
                                     (u/offset-time (u/current-time) -5 :seconds)
                                     sid))}))
      ;; an expired token
      (let [short (ua/account-id->jwt acc (:jwt-secret planted) 1 600)]
        (Thread/sleep 2100)
        (note! :expired (req :get "/admin/me" {:headers (bearer short)})))
      (note! :invalid (req :get "/admin/me" {:headers (bearer (str t1 "x"))}))
      ;; one-time token
      (let [ott (token-of (req :get "/admin/csv/auth" {:headers (bearer t1)}))]
        (note! :csv (req :get (format "/admin/csv?token=%s&property-paths=%s"
                                      ott (u/url-encode (str [["id"]])))
                         {:as :stream})))
      ;; xAPI: no credentials, a wrong secret, a valid read-only credential writing
      (note! :xapi-none (req :get "/xapi/statements" {:headers {"X-Experience-API-Version" "1.0.3"}}))
      (note! :xapi-bad (req :get "/xapi/statements"
                            {:headers (basic (:api-key planted) "not-the-secret")}))
      (note! :xapi-ok (req :get "/xapi/statements"
                           {:headers (basic (:api-key planted) (:api-secret planted))}))
      (let [{:strs [api-key secret-key]}
            (-> (req :post "/admin/creds" {:headers (bearer t1)
                                           :body (u/write-json-str {"scopes" ["statements/read"]})})
                :body u/parse-json)]
        (note! :xapi-scope (req :post "/xapi/statements"
                                {:headers (basic api-key secret-key) :body stmt})))
      ;; sign out, then use the signed-out session
      (note! :logout (req :post "/admin/account/logout" {:headers (bearer t1)}))
      (note! :session-ended (req :get "/admin/me" {:headers (bearer t0)})))
    @st))

(deftest route-events-test
  (let [sys (start)]
    (try
      (let [[st captured] (capture exercise!)
            lines (lines-of [st captured])
            has?  (fn [fmt & subs]
                    (boolean (some #(and (matches? fmt %)
                                         (every? (fn [s] (cstr/includes? % s)) subs))
                                   lines)))]
        (testing "the responses are as expected"
          (is (= {:login-failure 401 :me 200 :renew 200 :renew-expired 401
                  :expired 401 :invalid 401 :csv 200 :xapi-none 401 :xapi-bad 401
                  :xapi-ok 200 :xapi-scope 403 :logout 200 :session-ended 401}
                 st)))
        (testing "every line is one of the fixed formats"
          (is (seq lines))
          (is (every? (fn [l] (some #(matches? % l) (keys formats))) lines)))
        (testing "each event is logged"
          (is (has? :login-success))
          (is (has? :login-failure "user=\"authlogadmin\""))
          (is (has? :request-success "route=lrsql.admin.me/get" "method=local"))
          (is (has? :renew-success))
          (is (has? :renew-failure))
          (is (has? :request-refused "reason=expired"))
          (is (has? :request-refused "reason=invalid-token"))
          (is (has? :request-success "method=onetime"))
          (is (has? :xapi-refused "reason=no-credentials" "status=401"))
          (is (has? :xapi-refused "reason=invalid-credentials" "status=401"))
          (is (has? :xapi-refused "reason=scope" "status=403"))
          (is (has? :logout-success))
          (is (has? :request-refused "reason=session-ended")))
        (testing "successful xAPI requests are not logged"
          (is (= 3 (count (filter #(cstr/includes? % "event=xapi.request") lines))))))
      (finally
        (component/stop sys)))))

(deftest leak-test
  (let [sys (start)]
    (try
      (let [captured (capture exercise!)
            lines    (lines-of captured)
            secrets  (vals planted)]
        (testing "no password, API key, API secret, signing secret or token in any line"
          (is (seq lines))
          (is (= #{} (leaks lines secrets))))
        (testing "control: the same checker catches a planted leak"
          (is (= #{(:api-secret planted)}
                 (leaks (conj (vec lines) (str "AUTH x=" (:api-secret planted))) secrets)))
          ;; The token-shaped string is assembled here from its parts, so the
          ;; source holds no token-shaped literal.
          (let [part   #(.encodeToString (.withoutPadding (Base64/getUrlEncoder))
                                         (.getBytes ^String % "UTF-8"))
                jwtish (cstr/join "." [(part "{\"alg\":\"HS256\"}") (part "{\"x\":1}") (part "sig")])]
            (is (re-find jwt-shaped jwtish))
            (is (= #{:jwt} (leaks [(str "AUTH token=" jwtish)] secrets))))))
      (finally
        (component/stop sys)))))

(deftest long-username-test
  (let [sys (start)]
    (try
      (let [raw  (apply str (take 200 (cycle "user\u0001\u0002name\t")))
            [st captured] (capture #(:status (req :post "/admin/account/login"
                                                   {:headers json
                                                    :body (login-body raw "wrong")})))
            line (first (filter #(cstr/includes? % "event=admin.login") (lines-of [st captured])))
            user (some->> line (re-find #"user=\"([^\"]*)\"") second)]
        (is (= 401 st))
        (is (string? line))
        (is (and line (matches? :login-failure line)))
        (is (= 64 (count user)))
        (is (and user (re-matches #"[\x20-\x7E]{64}" user))))
      (finally
        (component/stop sys)))))
