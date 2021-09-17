(ns lrsql.concurrency-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as a]
            [com.stuartsierra.component :as component]
            [babashka.curl :as curl]
            [com.yetanalytics.datasim.input :as sim-input]
            [com.yetanalytics.datasim.sim   :as sim]
            [xapi-schema.spec :as xs]
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
    (a/pipeline-blocking conc-size
                         res-chan
                         (map post-fn)
                         req-chan)
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
        endpoint    "http://localhost:8080/xapi/statements"
        num-stmts   1000
        batch-size  10
        num-threads 10
        query-mult  10]
    (testing "concurrent insertions"
      (let [insert-reqs (->> (test-statements num-stmts)
                             (partition batch-size)
                             (map (fn [batch]
                                    {:headers    headers
                                     :basic-auth basic-auth
                                     :body       (String. (u/write-json
                                                           (vec batch)))})))
            insert-res  (do-async-op! curl/post
                                      endpoint
                                      insert-reqs
                                      num-threads)]
        (is (= (/ num-stmts batch-size)
               (count insert-res)))
        (is (every? (fn [res]
                      (cond ;; Best we can do to catch PG deadlock exn
                        (instance? clojure.lang.ExceptionInfo res)
                        (let [exd (ex-data res)]
                          (and (= 500 (:status exd))
                               (= "org.postgresql.util.PSQLException"
                                  (-> exd
                                      :body
                                      u/parse-json
                                      (get-in ["error" "type" "name"])))))

                        (= 200 (:status res))
                        (= batch-size (-> res
                                          :body
                                          (u/parse-json :object? false)
                                          count))

                        :else
                        false))
                    insert-res))))
    (testing "concurrent queries"
      (let [query-reqs (->> test-queries
                            (mapcat (partial repeat query-mult))
                            (map (fn [query]
                                   {:headers      headers
                                    :basic-auth   basic-auth
                                    :query-params (not-empty query)})))
            query-res  (do-async-op! curl/get
                                     endpoint
                                     query-reqs
                                     num-threads)]
        (is (= (* query-mult (count test-queries))
               (count query-res)))
        (is (every? (fn [res]
                      (cond ;; Queries should never deadlock
                        (= 200 (:status res))
                        (s/valid? (s/coll-of ::xs/statement)
                                  (-> res
                                      :body
                                      u/parse-json
                                      (get "statements")))

                        :else
                        false))
                    query-res))))
    (component/stop sys')))
