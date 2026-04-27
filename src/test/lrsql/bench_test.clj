(ns lrsql.bench-test
  "Testing for inserting and downloading large amounts of statements."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [babashka.curl :as curl]
            [java-time.api :as jt]
            [lrsql.util :as u]
            [lrsql.test-support :as support]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Increase or decrease as needed
(def num-statements 5000)

(def batch-size 50)

(def headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def api-keys
  ["username" "password"])

(deftest bench-test
  (let [sys           (support/test-system)
        sys'          (component/start sys)
        url-prefix    (-> sys' :webserver :config :url-prefix)
        stmt-endpoint (format "http://localhost:8080%s/statements" url-prefix)
        statements    (support/bench-statements* num-statements)]
    (try
      (testing "Inserting large amounts of data"
        (let [start (jt/instant)]
          (loop [batches (partition-all batch-size statements)
                 fail?   false]
            (if-some [batch (first batches)]
              (let [{:keys [status]}
                    (try (curl/post stmt-endpoint
                                    {:headers    headers
                                     :basic-auth api-keys
                                     :body       (u/write-json-str (vec batch))})
                         (catch Exception e e))]
                (recur (rest batches)
                       (or fail? (not= 200 status))))
              (let [end    (jt/instant)
                    t-diff (jt/time-between start end :seconds)]
                (log/infof "Inserted %d statements in %s seconds"
                           num-statements
                           t-diff)
                (is (not fail?)))))))
      (testing "Querying large amounts of data"
        (let [start (jt/instant)]
          (loop [query-url  stmt-endpoint
                 stmt-count 0
                 fail?      false]
            (let [{:keys [status body]}
                  (try (curl/get query-url
                                 {:headers    headers
                                  :basic-auth api-keys})
                       (catch Exception e e))
                  {:keys [statements more]}
                  (some-> body (u/parse-json :keyword-keys? true))
                  query-url*
                  (str "http://localhost:8080" more)
                  stmt-count*
                  (+ stmt-count (count statements))]
              (if (and (= 200 status)
                       (not-empty more))
                (recur query-url*
                       stmt-count*
                       (or fail? (not= 200 status)))
                (let [end    (jt/instant)
                      t-diff (jt/time-between start end :seconds)]
                  (log/infof "Queried %d statements in %s seconds"
                             num-statements
                             t-diff)
                  (is (not fail?))
                  (is (= num-statements stmt-count*))))))))
      (testing "Downloading large amounts of data"
        (let [;; Log in and get the one-time JWT
              json-web-token
              (-> (curl/post "http://localhost:8080/admin/account/login"
                             {:headers headers
                              :body    (u/write-json-str
                                        {"username" "username"
                                         "password" "password"})})
                  :body
                  (u/parse-json :keyword-keys? true)
                  :json-web-token)
              headers*
              {"Authorization" (str "Bearer " json-web-token)}
              json-web-token*
              (-> (curl/get "http://localhost:8080/admin/csv/auth"
                            {:headers headers*})
                  :body
                  (u/parse-json :keyword-keys? true)
                  :json-web-token)
              csv-endpoint
              (format "http://localhost:8080/admin/csv?token=%s&property-paths=%s"
                      json-web-token*
                      (u/url-encode [["id"] ["verb" "id"]]))
              ;; Download CSV
              start
              (jt/instant)
              {:keys [status] input-stream :body}
              (curl/get csv-endpoint {:as :stream})]
          (with-open [reader (io/reader input-stream)]
            (let [res-count (count (csv/read-csv reader))
                  end       (jt/instant)
                  t-diff    (jt/time-between start end :seconds)]
              (log/infof "Downloaded CSV of %d statements in %s seconds"
                         num-statements
                         t-diff)
              (is (= 200 status))
              (is (= (inc num-statements) res-count))))))
      (finally (component/stop sys')))))
