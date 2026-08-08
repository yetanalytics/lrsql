(ns lrsql.etag-race-test
  "Tests for atomic etag validation — verifying that the TOCTOU race
   condition on document PUT/POST with If-Match headers is prevented."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.core.async :as a]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc])
  (:import [java.security MessageDigest]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

(def state-params
  {:stateId    "race-test-state"
   :activityId "https://example.org/race-activity"
   :agent      {"mbox" "mailto:racer@example.org"}})

(defn- make-doc [content]
  (let [bs (.getBytes content "UTF-8")]
    {:content-length (count bs)
     :content-type   "application/json"
     :contents       bs}))

(defn- ctx-with-headers
  "Build a ctx map with If-Match / If-None-Match request headers."
  [& {:keys [if-match if-none-match]}]
  (cond-> {:com.yetanalytics.lrs/version "1.0.3"
           :request {:headers {}}}
    if-match
    (assoc-in [:request :headers "if-match"] if-match)
    if-none-match
    (assoc-in [:request :headers "if-none-match"] if-none-match)))

(defn- compute-sha1
  "Compute SHA-1 hex string from bytes — matches the upstream LRS etag computation."
  ^String [^bytes bs]
  (apply str
         (map #(.substring (Integer/toString (+ (bit-and % 0xff) 0x100) 16) 1)
              (.digest (MessageDigest/getInstance "SHA-1") bs))))

(defn- get-etag
  "Get the current etag for a document by reading it and computing SHA-1."
  [lrs params]
  (let [{:keys [document]} (lrsp/-get-document lrs tc/ctx auth-ident params)]
    (when-let [contents (:contents document)]
      (compute-sha1 (if (bytes? contents)
                      contents
                      (.getBytes (str contents) "UTF-8"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest etag-precondition-basic-test
  (testing "If-Match against existing document"
    (let [sys  (support/test-system)
          sys' (component/start sys)
          lrs  (:lrs sys')]
      (try
        ;; Create initial document (no headers needed)
        (lrsp/-set-document lrs tc/ctx auth-ident state-params
                            (make-doc "{\"v\":1}") false)
        (let [etag (get-etag lrs state-params)]
          (is (some? etag) "Document should exist and have an etag")

          (testing "matching etag allows write"
            (let [result (lrsp/-set-document
                          lrs
                          (ctx-with-headers :if-match etag)
                          auth-ident state-params
                          (make-doc "{\"v\":2}") false)]
              (is (nil? (:error result))
                  "Write with matching etag should succeed")))

          (testing "stale etag is rejected"
            (let [result (lrsp/-set-document
                          lrs
                          (ctx-with-headers :if-match etag)
                          auth-ident state-params
                          (make-doc "{\"v\":3}") false)]
              (is (some? (:error result))
                  "Write with stale etag should be rejected"))))
        (finally (component/stop sys')))))

  (testing "If-None-Match * prevents overwrite"
    (let [sys  (support/test-system)
          sys' (component/start sys)
          lrs  (:lrs sys')]
      (try
        ;; First write with If-None-Match * should succeed (no doc exists)
        (let [result (lrsp/-set-document
                      lrs
                      (ctx-with-headers :if-none-match "*")
                      auth-ident state-params
                      (make-doc "{\"v\":1}") false)]
          (is (nil? (:error result))
              "If-None-Match * on empty should succeed"))

        ;; Second write with If-None-Match * should fail (doc exists)
        (let [result (lrsp/-set-document
                      lrs
                      (ctx-with-headers :if-none-match "*")
                      auth-ident state-params
                      (make-doc "{\"v\":2}") false)]
          (is (some? (:error result))
              "If-None-Match * on existing doc should fail"))
        (finally (component/stop sys'))))))

(deftest etag-race-condition-test
  (testing "concurrent PUT with same If-Match etag — only one should win"
    (let [sys         (support/test-system)
          sys'        (component/start sys)
          lrs         (:lrs sys')
          num-threads 10]
      (try
        ;; Create initial document
        (lrsp/-set-document lrs tc/ctx auth-ident state-params
                            (make-doc "{\"initial\":true}") false)
        (let [etag (get-etag lrs state-params)]
          (is (some? etag) "Should have initial etag")

          ;; Fire concurrent writes all using the same stale etag
          (let [results
                (let [result-chan (a/chan num-threads)]
                  (dotimes [i num-threads]
                    (a/thread
                      (let [res (try
                                  (lrsp/-set-document
                                   lrs
                                   (ctx-with-headers :if-match etag)
                                   auth-ident state-params
                                   (make-doc (format "{\"writer\":%d}" i))
                                   false)
                                  (catch Exception e
                                    {:error e}))]
                        (a/>!! result-chan res))))
                  (repeatedly num-threads #(a/<!! result-chan)))

                successes (filter #(nil? (:error %)) results)
                failures  (filter #(some? (:error %)) results)]

            (testing "exactly one concurrent write should succeed"
              (is (= 1 (count successes))
                  (format "Expected 1 success but got %d out of %d"
                          (count successes) num-threads)))

            (testing "all other writes should be rejected"
              (is (= (dec num-threads) (count failures))
                  (format "Expected %d failures but got %d"
                          (dec num-threads) (count failures))))))
        (finally (component/stop sys'))))))

(deftest etag-race-condition-post-merge-test
  (testing "concurrent POST (merge) with same If-Match etag — only one should win"
    (let [sys         (support/test-system)
          sys'        (component/start sys)
          lrs         (:lrs sys')
          num-threads 10]
      (try
        ;; Create initial JSON document for merge
        (lrsp/-set-document lrs tc/ctx auth-ident state-params
                            (make-doc "{\"base\":true}") true)
        (let [etag (get-etag lrs state-params)]
          (is (some? etag) "Should have initial etag")

          (let [results
                (let [result-chan (a/chan num-threads)]
                  (dotimes [i num-threads]
                    (a/thread
                      (let [res (try
                                  (lrsp/-set-document
                                   lrs
                                   (ctx-with-headers :if-match etag)
                                   auth-ident state-params
                                   (make-doc (format "{\"merger\":%d}" i))
                                   true)
                                  (catch Exception e
                                    {:error e}))]
                        (a/>!! result-chan res))))
                  (repeatedly num-threads #(a/<!! result-chan)))

                successes (filter #(nil? (:error %)) results)
                failures  (filter #(some? (:error %)) results)]

            (testing "exactly one concurrent merge should succeed"
              (is (= 1 (count successes))
                  (format "Expected 1 success but got %d out of %d"
                          (count successes) num-threads)))

            (testing "all other merges should be rejected"
              (is (= (dec num-threads) (count failures))
                  (format "Expected %d failures but got %d"
                          (dec num-threads) (count failures))))))
        (finally (component/stop sys'))))))
