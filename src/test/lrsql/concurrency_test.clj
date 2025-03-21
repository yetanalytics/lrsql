(ns lrsql.concurrency-test
  "Tests for concurrent insertions and queries."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as a]
            [com.stuartsierra.component :as component]
            [babashka.curl :as curl]
            [xapi-schema.spec :as xs]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def basic-auth
  ["username" "password"])

(deftest concurrency-test
  (let [sys        (support/test-system)
        sys'       (component/start sys)
        url-prefix (-> sys' :webserver :config :url-prefix)
        ;; Parameters
        endpoint    (format "http://localhost:8080%s/statements" url-prefix)
        num-stmts   100
        batch-size  5
        num-threads 5
        query-mult  5]
    (testing "concurrent insertions"
      (let [insert-reqs (->> (support/bench-statements num-stmts)
                             (partition batch-size)
                             (map (fn [batch]
                                    {:headers    headers
                                     :basic-auth basic-auth
                                     :body       (u/write-json-str (vec batch))})))
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
      (let [query-reqs (->> support/bench-queries
                            (mapcat (partial repeat query-mult))
                            (map (fn [query]
                                   {:headers      headers
                                    :basic-auth   basic-auth
                                    :query-params (not-empty query)})))
            query-res  (do-async-op! curl/get
                                     endpoint
                                     query-reqs
                                     num-threads)]
        (is (= (* query-mult (count support/bench-queries))
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
