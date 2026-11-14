(ns lrsql.json-depth-test
  "Black-box over HTTP, so it loads, and its tests fail rather than error, on
   a tree without the change."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u])
  (:import [java.io ByteArrayInputStream]
           [java.util Base64]))

(use-fixtures :each support/fresh-db-fixture)

(def ^:private base "http://0.0.0.0:8080")

(defn- xapi-headers [sys ctype]
  (let [{:keys [api-key-default api-secret-default]} (get-in sys [:lrs :config])]
    {"Authorization" (str "Basic " (.encodeToString
                                    (Base64/getEncoder)
                                    (.getBytes (str api-key-default ":" api-secret-default)
                                               "UTF-8")))
     "X-Experience-API-Version" "1.0.3"
     "Content-Type" ctype}))

(defn- req
  "The status of a request. The body goes to curl as a stream: a string body
   is passed on curl's command line, which a 200 KB body overflows."
  [method path headers body]
  (-> ((case method :get curl/get :post curl/post :put curl/put)
       (str base path)
       (cond-> {:throw false :headers headers}
         body (assoc :body (ByteArrayInputStream. (.getBytes ^String body "UTF-8")))))
      :status))

(defn- deep [n]
  (str (apply str (repeat n "[")) (apply str (repeat n "]"))))

(defn- statement-at-depth
  "A valid statement whose whole body nests exactly `depth` levels: the
   statement is level 1, result 2, extensions 3, then nested arrays."
  [depth]
  (let [inner (- depth 3)]
    (str "{\"actor\":{\"mbox\":\"mailto:depth@example.org\",\"objectType\":\"Agent\"},"
         "\"verb\":{\"id\":\"http://adlnet.gov/expapi/verbs/answered\"},"
         "\"object\":{\"id\":\"http://www.example.com/depth\"},"
         "\"result\":{\"extensions\":{\"http://www.example.com/x\":" (deep inner) "}}}")))

(def ^:private stmt (statement-at-depth 4))

(def ^:private state-path
  (str "/xapi/activities/state?activityId=" (u/url-encode "http://www.example.com/a")
       "&agent=" (u/url-encode "{\"mbox\":\"mailto:depth@example.org\"}")
       "&stateId="))

(defn- multipart [boundary first-part]
  (str "--" boundary "\r\nContent-Type: application/json\r\n\r\n"
       first-part "\r\n--" boundary "--\r\n"))

(defn- with-sys [f]
  (let [sys (component/start (support/test-system))]
    (try (f sys) (finally (component/stop sys)))))

(deftest deep-json-refused-test
  (with-sys
    (fn [sys]
      (let [json  (xapi-headers sys "application/json")
            body  (deep 100000)]
        (testing "100,000 levels: 400 on statements, POST and PUT"
          (is (= 400 (req :post "/xapi/statements" json body)))
          (is (= 400 (req :put (str "/xapi/statements?statementId=" (random-uuid)) json body))))
        (testing "with a charset parameter"
          (is (= 400 (req :post "/xapi/statements"
                          (xapi-headers sys "application/json; charset=utf-8") body))))
        (testing "unauthenticated admin sign-in"
          (is (= 400 (req :post "/admin/account/login" {"Content-Type" "application/json"} body))))
        (testing "the first part of a multipart statement body"
          (is (= 400 (req :post "/xapi/statements"
                          (xapi-headers sys "multipart/mixed; boundary=depthb")
                          (multipart "depthb" body)))))
        (testing "a JSON state document"
          (is (= 400 (req :put (str state-path "deep") json body))))
        (testing "depths that got past the parser and overflowed later"
          (doseq [d [2000 20000]]
            (is (= 400 (req :post "/xapi/statements" json (statement-at-depth d))))))))))

(deftest depth-boundary-test
  (with-sys
    (fn [sys]
      (let [json (xapi-headers sys "application/json")]
        (testing "control: a realistic statement and a JSON document are accepted"
          (is (= 200 (req :post "/xapi/statements" json stmt)))
          (is (= 204 (req :put (str state-path "ok") json "{\"a\":{\"b\":[1,2,{\"c\":3}]}}"))))
        (testing "a JSON document nesting exactly 64 levels is accepted, 65 is not"
          (is (= 204 (req :put (str state-path "d64") json (deep 64))))
          (is (= 400 (req :put (str state-path "d65") json (deep 65)))))
        (testing "a statement nesting exactly 64 levels passes the check, 65 does not"
          (if (= "mariadb" (get-in sys [:connection :config :database :db-type]))
            ;; MariaDB's JSON_VALID check on the stored statement has a
            ;; lower nesting limit of its own, so only the depth check's
            ;; decision is asserted here.
            (is (not= 400 (req :post "/xapi/statements" json (statement-at-depth 64))))
            (is (= 200 (req :post "/xapi/statements" json (statement-at-depth 64)))))
          (is (= 400 (req :post "/xapi/statements" json (statement-at-depth 65)))))
        (testing "brackets inside strings, after an escaped quote too, do not count"
          (let [body (cstr/replace stmt "verbs/answered\"}"
                                   (str "verbs/answered\",\"display\":{\"en-US\":\""
                                        (deep 100) "\\\"[[[\"}}"))]
            (is (cstr/includes? body "\"display\""))
            (is (= 200 (req :post "/xapi/statements" json body)))))))))

(deftest edn-transit-test
  (with-sys
    (fn [sys]
      (let [deep-edn (deep 100000)]
        (testing "EDN to the unauthenticated sign-in: 415, never 500"
          (is (= 415 (req :post "/admin/account/login" {"Content-Type" "application/edn"} deep-edn))))
        (testing "Transit and msgpack to statements: 415"
          (is (= 415 (req :post "/xapi/statements"
                          (xapi-headers sys "application/transit+json") deep-edn)))
          (is (= 415 (req :post "/xapi/statements"
                          (xapi-headers sys "application/transit+msgpack") "x"))))
        (testing "control: an EDN state document is stored and read back as bytes"
          (is (= 204 (req :put (str state-path "edn")
                          (xapi-headers sys "application/edn") "{:a [1 2]}")))
          (is (= "{:a [1 2]}"
                 (:body (curl/get (str base state-path "edn")
                                  {:throw false :headers (xapi-headers sys "application/edn")})))))))))

(deftest multipart-first-part-cap-test
  (with-sys
    (fn [sys]
      (let [hdr (xapi-headers sys "multipart/mixed; boundary=capb")]
        (testing "a first part that does not end within 8 MiB: 413"
          (is (= 413 (req :post "/xapi/statements" hdr
                          (str "--capb\r\nContent-Type: application/json\r\n\r\n"
                               (apply str (repeat (* 9 1024 1024) "a")))))))
        (testing "control: a multipart statement with a normal first part is accepted"
          (is (= 200 (req :post "/xapi/statements" hdr (multipart "capb" stmt)))))))))

(deftest repeated-deep-requests-test
  (with-sys
    (fn [sys]
      (let [json  (xapi-headers sys "application/json")
            body  (deep 100000)
            t0    (System/nanoTime)
            codes (frequencies (repeatedly 50 #(req :post "/xapi/statements" json body)))
            ms    (/ (- (System/nanoTime) t0) 1e6)]
        (println (format "INSTRUMENT: 50 sequential 100,000-deep requests in %.0f ms (%.1f ms each): %s"
                         ms (/ ms 50) codes))
        (is (= {400 50} codes))
        (testing "the server stays healthy"
          (is (= 200 (req :get "/health" {} nil)))
          (is (= 200 (req :post "/xapi/statements" json stmt))))))))
