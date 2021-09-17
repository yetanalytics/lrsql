(ns lrsql.concurrency-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.core.async :as a]
            [com.stuartsierra.component :as component]
            [babashka.curl :as curl]
            [com.yetanalytics.datasim.input :as sim-input]
            [com.yetanalytics.datasim.sim   :as sim]
            [lrsql.backend.protocol :as bp]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

;; Init

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

(defn test-statements
  [num-stmts]
  (->> "dev-resources/default/insert_input.json"
       (sim-input/from-location :input :json)
       sim/sim-seq
       (take num-stmts)
       (into [])))

(def test-queries
  (-> "dev-resources/default/query_input.json"
      slurp
      (u/parse-json :object? false)))

;; Tests

(defn- do-async-op!
  "Enter the async zone and perform `curl-op` on `endpoint` and `requests`
   on `conc-size` concurrent threads."
  [curl-op endpoint requests conc-size]
  (let [post-fn (fn [req]
                  (try (curl-op endpoint req)
                       (catch Exception e e)))
        req-chan (a/to-chan! requests)
        res-chan (a/chan (count requests))]
    (a/<!! (a/pipeline-blocking conc-size
                                res-chan
                                (map post-fn)
                                req-chan))
    (a/<!! (a/into [] res-chan))))

(deftest concurrency-test
  (let [sys        (support/test-system)
        sys'       (component/start sys)
        backend    (:backend sys')
        ;; Curl
        headers    {"Content-Type"             "application/json"
                    "X-Experience-API-Version" "1.0.3"}
        basic-auth ["username" "password"]
        ;; Parameters
        num-stmts   1000
        batch-size  10
        num-threads 10
        query-mult  10]
    (testing "concurrent insertions"
      (try
        (let [insert-reqs (->> (test-statements num-stmts)
                               (partition batch-size)
                               (map (fn [batch]
                                      {:headers    headers
                                       :basic-auth basic-auth
                                       :body       (String. (u/write-json
                                                             (vec batch)))})))
              insert-res  (do-async-op! curl/post
                                        "http://localhost:8080/xapi"
                                        insert-reqs
                                        num-threads)]
          (is (= (/ num-stmts batch-size)
                 (count insert-res))))
        (catch Exception e
          (bp/-txn-retry? backend e))))
    (testing "concurrent queries"
      (try
        (let [query-reqs (->> test-queries
                              (mapcat (partial repeat query-mult))
                              (map (fn [query]
                                     {:headers      headers
                                      :basic-auth   basic-auth
                                      :query-params (not-empty query)})))
              query-res  (do-async-op! curl/get
                                       "http://localhost:8080/xapi"
                                       query-reqs
                                       num-threads)]
          (is (= (* query-mult (count test-queries))
                 (count query-res))))
        (catch Exception e
          (bp/-txn-retry? backend e))))
    (component/stop sys')))
