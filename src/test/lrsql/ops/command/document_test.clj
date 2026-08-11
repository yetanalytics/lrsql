(ns lrsql.ops.command.document-test
  (:require [clojure.test :refer [deftest is]]
            [com.yetanalytics.lrs.util.hash :as hash]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.backend.protocol :as bp]
            [lrsql.ops.command.document :as command]
            [lrsql.util :as u]
            [lrsql.util.concurrency :as concurrency]
            [lrsql.util.document :as document])
  (:import [java.nio.charset StandardCharsets]
           [java.util Arrays UUID]))

(defn- utf8-bytes
  [s]
  (.getBytes ^String s StandardCharsets/UTF_8))

(defn- raw-document
  [body]
  (let [contents (utf8-bytes body)]
    {:contents       contents
     :content_type   "application/json"
     :content_length (count contents)
     :last_modified  (u/current-time)}))

(defn- document-input
  [body]
  (let [contents (utf8-bytes body)]
    {:table          :activity-profile-document
     :primary-key    (UUID/randomUUID)
     :profile-id     "profile-id"
     :activity-iri   "https://example.org/activity"
     :last-modified  (u/current-time)
     :content-type   "application/json"
     :content-length (count contents)
     :contents       contents}))

(defn- conflicting-backend
  [state winner-body query-count]
  (let [first-update? (atom true)
        first-delete? (atom true)]
    (reify
      bp/BackendUtil
      (-txn-retry? [_ _] false)

      bp/ActivityProfileDocumentBackend
      (-insert-activity-profile-document! [_ _ _] nil)
      (-insert-activity-profile-document-if-absent! [_ _ _] false)
      (-update-activity-profile-document! [_ _ _] nil)
      (-update-activity-profile-document-if-contents! [_ _ input]
        (if (compare-and-set! first-update? true false)
          (do
            (reset! state (raw-document winner-body))
            false)
          (if (Arrays/equals ^bytes (:expected-contents input)
                             ^bytes (:contents @state))
            (do
              (swap! state assoc
                     :contents (:contents input)
                     :content_length (:content-length input)
                     :last_modified (:last-modified input))
              true)
            false)))
      (-delete-activity-profile-document! [_ _ _] nil)
      (-delete-activity-profile-document-if-contents! [_ _ input]
        (if (compare-and-set! first-delete? true false)
          (do
            (reset! state (raw-document winner-body))
            false)
          (if (Arrays/equals ^bytes (:expected-contents input)
                             ^bytes (:contents @state))
            (do (reset! state nil) true)
            false)))
      (-query-activity-profile-document [_ _ _]
        (swap! query-count inc)
        @state)
      (-query-activity-profile-document-ids [_ _ _] [])
      (-query-activity-profile-document-exists [_ _ _] (some? @state)))))

(defn- run-set-with-retries
  [backend ctx input merge?]
  (concurrency/rerunable-txn*
   #(command/set-document-cas! backend ::tx ctx input merge?)
   0
   (assoc (document/document-retry-opts
           backend
           {:stmt-retry-limit  2
            :stmt-retry-budget 1})
          :j-range 0)))

(defn- run-delete-with-retries
  [backend ctx input]
  (concurrency/rerunable-txn*
   #(command/delete-document-cas! backend ::tx ctx input)
   0
   (assoc (document/document-retry-opts
           backend
           {:stmt-retry-limit  2
            :stmt-retry-budget 1})
          :j-range 0)))

(deftest stale-precondition-after-cas-conflict-test
  (let [initial-body "{\"value\":\"initial\"}"
        winner-body  "{\"value\":\"winner\"}"
        state        (atom (raw-document initial-body))
        query-count  (atom 0)
        backend      (conflicting-backend state winner-body query-count)
        ctx          {::lrs-doc/preconditions
                      {:if-match #{(hash/sha-1 initial-body)}}}
        result       (run-set-with-retries backend
                                           ctx
                                           (document-input
                                            "{\"value\":\"stale\"}")
                                           false)]
    (is (= 2 @query-count) "the CAS miss causes a fresh read")
    (is (lrs-doc/precondition-failed? (:error result)))
    (is (= {"value" "winner"}
           (u/parse-json (:contents @state)))
        "the stale retry cannot overwrite the winner")))

(deftest post-merge-is-recomputed-after-cas-conflict-test
  (let [state       (atom (raw-document "{\"initial\":true}"))
        query-count (atom 0)
        backend     (conflicting-backend state
                                         "{\"winner\":true}"
                                         query-count)
        result      (run-set-with-retries backend
                                          {}
                                          (document-input
                                           "{\"request\":true}")
                                          true)]
    (is (= {} result))
    (is (= 2 @query-count) "the merge is retried from a fresh read")
    (is (= {"winner" true "request" true}
           (u/parse-json (:contents @state)))
        "the retried merge includes the winning representation")))

(deftest unexpected-exception-is-preserved-test
  (let [state       (atom (raw-document "{\"value\":\"initial\"}"))
        query-count (atom 0)
        backend     (conflicting-backend state
                                         "{\"value\":\"winner\"}"
                                         query-count)
        expected    (ex-info "Unexpected" {:type ::unexpected})
        thrown      (try
                      (with-redefs
                        [bp/-query-activity-profile-document
                         (fn [& _] (throw expected))]
                        (run-set-with-retries backend
                                              {}
                                              (document-input
                                               "{\"value\":\"new\"}")
                                              false))
                      nil
                      (catch clojure.lang.ExceptionInfo ex
                        ex))]
    (is (identical? expected thrown))
    (is (not (document/cas-conflict? thrown)))
    (is (not (lrs-doc/precondition-failed? thrown)))))

(deftest stale-delete-precondition-after-cas-conflict-test
  (let [initial-body "{\"value\":\"initial\"}"
        winner-body  "{\"value\":\"winner\"}"
        state        (atom (raw-document initial-body))
        query-count  (atom 0)
        backend      (conflicting-backend state winner-body query-count)
        ctx          {::lrs-doc/preconditions
                      {:if-match #{(hash/sha-1 initial-body)}}}
        result       (run-delete-with-retries backend
                                              ctx
                                              (document-input initial-body))]
    (is (= 2 @query-count) "the delete CAS miss causes a fresh read")
    (is (lrs-doc/precondition-failed? (:error result)))
    (is (= {"value" "winner"}
           (u/parse-json (:contents @state)))
        "the stale retry cannot delete the winner")))
