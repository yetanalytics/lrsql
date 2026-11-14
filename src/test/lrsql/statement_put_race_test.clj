(ns lrsql.statement-put-race-test
  "Concurrent PUTs of one statement ID, over HTTP. With SQLite the test
   database is a single connection, so the requests run one after another and
   the race cannot happen there; the test still checks the outcome."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u])
  (:import [java.util.concurrent CountDownLatch]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(def ^:private headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(defn- stmt
  [n]
  (u/write-json-str
   {"actor"  {"mbox" "mailto:race@example.org" "objectType" "Agent"}
    "verb"   {"id" "http://adlnet.gov/expapi/verbs/answered"}
    "object" {"id" "http://www.example.com/race"}
    "result" {"score" {"raw" n}}}))

(defn- race!
  "PUT each of `bodies` to one new statement ID, all released at once. Return
   `[id {status count}]`."
  [bodies]
  (let [id   (str (random-uuid))
        gate (CountDownLatch. 1)
        futs (mapv (fn [b]
                     (future
                       (.await gate)
                       (:status
                        (curl/put (str "http://0.0.0.0:8080/xapi/statements?statementId="
                                       id)
                                  {:headers    headers
                                   :basic-auth ["username" "password"]
                                   :body       b
                                   :throw      false}))))
                   bodies)]
    (.countDown gate)
    [id (frequencies (map deref futs))]))

(deftest concurrent-put-test
  (let [sys (component/start (support/test-system))]
    (try
      (testing "identical PUTs of one ID all answer 204, and the statement reads back"
        (dotimes [_ 5]
          (let [[id st] (race! (repeat 20 (stmt 1)))]
            (is (= {204 20} st))
            (is (= 200
                   (:status
                    (curl/get (str "http://0.0.0.0:8080/xapi/statements?statementId="
                                   id)
                              {:headers    headers
                               :basic-auth ["username" "password"]
                               :throw      false})))))))
      (testing "different statements under one ID: one 204, the rest 409, never 500"
        (dotimes [_ 5]
          (let [[_ st] (race! (map stmt (range 20)))]
            (is (= {204 1 409 19} st)))))
      (finally
        (component/stop sys)))))
