(ns lrsql.util.document-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.yetanalytics.lrs.util.hash :as hash]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.backend.protocol :as bp]
            [lrsql.util.concurrency :as concurrency]
            [lrsql.util.document :as document])
  (:import [java.nio.charset StandardCharsets]))

(defn- utf8-bytes
  [s]
  (.getBytes ^String s StandardCharsets/UTF_8))

(def retrying-backend
  (reify bp/BackendUtil
    (-txn-retry? [_ ex]
      (= ::retryable-database-error (:type (ex-data ex))))))

(deftest normalized-preconditions-test
  (let [contents (utf8-bytes "{\"value\":\"current\"}")
        etag     (hash/sha-1 contents)
        current  {:contents contents}]
    (testing "context contract"
      (is (= {} (document/preconditions {})))
      (is (= {:if-match #{etag}}
             (document/preconditions
              {::lrs-doc/preconditions {:if-match #{etag}}})))
      (is (document/preconditions-met?
           {::lrs-doc/preconditions {:if-match #{etag}}
            :request {:headers {"if-match" "\"ignored-header\""}}}
           current)
          "only normalized context preconditions are used"))

    (testing "observed representation state"
      (is (= {:exists? false}
             (document/current-document-state nil)))
      (is (= {:exists? true :etag etag}
             (document/current-document-state current))))

    (testing "wildcards and tag sets"
      (is (document/preconditions-met? {} nil))
      (is (document/preconditions-met? {} current))
      (is (document/preconditions-met?
           {::lrs-doc/preconditions {:if-match :*}}
           current))
      (is (not (document/preconditions-met?
                {::lrs-doc/preconditions {:if-match :*}}
                nil)))
      (is (document/preconditions-met?
           {::lrs-doc/preconditions {:if-none-match :*}}
           nil))
      (is (not (document/preconditions-met?
                {::lrs-doc/preconditions {:if-none-match :*}}
                current)))
      (is (document/preconditions-met?
           {::lrs-doc/preconditions {:if-match #{"other" etag}}}
           current))
      (is (not (document/preconditions-met?
                {::lrs-doc/preconditions {:if-match #{"other"}}}
                current)))
      (is (document/preconditions-met?
           {::lrs-doc/preconditions {:if-none-match #{"other"}}}
           current))
      (is (not (document/preconditions-met?
                {::lrs-doc/preconditions {:if-none-match #{etag}}}
                current))))

    (testing "standardized failure"
      (is (nil? (document/precondition-error
                 {::lrs-doc/preconditions {:if-match #{etag}}}
                 current)))
      (is (nil? (document/precondition-error
                 {::lrs-doc/preconditions {:if-none-match :*}}
                 nil)))
      (is (lrs-doc/precondition-failed?
           (:error
            (document/precondition-error
             {::lrs-doc/preconditions {:if-match :*}}
             nil))))
      (let [{:keys [error] :as result}
            (document/precondition-error
             {::lrs-doc/preconditions {:if-match #{"stale"}}}
             current
             {:operation :put})]
        (is (some? result))
        (is (lrs-doc/precondition-failed? error))
        (is (= :put (:operation (ex-data error))))))))

(deftest document-retry-orchestration-test
  (testing "retry classification"
    (let [cas-ex (document/cas-conflict-ex {:operation :post})
          db-ex  (ex-info "Retry database error"
                          {:type ::retryable-database-error})
          bad-ex (ex-info "Unexpected error" {:type ::unexpected})]
      (is (document/cas-conflict? cas-ex))
      (is (= :post (:operation (ex-data cas-ex))))
      (is (not (lrs-doc/precondition-failed? cas-ex)))
      (is (document/document-txn-retry? retrying-backend cas-ex))
      (is (document/document-txn-retry? retrying-backend db-ex))
      (is (not (document/document-txn-retry? retrying-backend bad-ex)))))

  (testing "existing retry configuration"
    (let [opts (document/document-retry-opts
                retrying-backend
                {:stmt-retry-limit  7
                 :stmt-retry-budget 250})]
      (is (= 7 (:max-attempt opts)))
      (is (= 250 (:budget opts)))
      (is ((:retry-test opts) (document/cas-conflict-ex)))
      (is ((:retry-test opts)
           (ex-info "Retry database error"
                    {:type ::retryable-database-error})))))

  (testing "CAS retry obtains a fresh attempt"
    (let [attempts (atom 0)
          result   (concurrency/rerunable-txn*
                    (fn []
                      (if (= 1 (swap! attempts inc))
                        (document/throw-cas-conflict!)
                        :applied))
                    0
                    (assoc (document/document-retry-opts
                            retrying-backend
                            {:stmt-retry-limit  2
                             :stmt-retry-budget 1})
                           :j-range 0))]
      (is (= :applied result))
      (is (= 2 @attempts))))

  (testing "retry exhaustion remains a CAS conflict, not a 412"
    (let [attempts (atom 0)
          thrown   (try
                     (concurrency/rerunable-txn*
                      (fn []
                        (swap! attempts inc)
                        (document/throw-cas-conflict!))
                      0
                      (assoc (document/document-retry-opts
                              retrying-backend
                              {:stmt-retry-limit  2
                               :stmt-retry-budget 1})
                             :j-range 0))
                     nil
                     (catch clojure.lang.ExceptionInfo ex
                       ex))]
      (is (= 3 @attempts))
      (is (document/cas-conflict? thrown))
      (is (not (lrs-doc/precondition-failed? thrown))))))
