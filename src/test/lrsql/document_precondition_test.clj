(ns lrsql.document-precondition-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.util.hash :as hash]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.test-constants :as tc]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

(use-fixtures :once support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(def resource-cases
  [{:label  "State"
    :id-key :stateId
    :params {:stateId    "state"
             :activityId "https://example.org/precondition/activity"
             :agent      {"mbox" "mailto:state@example.org"}}}
   {:label  "Agent Profile"
    :id-key :profileId
    :params {:profileId "agent-profile"
             :agent     {"mbox" "mailto:profile@example.org"}}}
   {:label  "Activity Profile"
    :id-key :profileId
    :params {:profileId  "activity-profile"
             :activityId "https://example.org/precondition/activity"}}])

(def initial-body "{\"value\":\"initial\"}")
(def replacement-body "{\"value\":\"replacement\"}")
(def merge-body "{\"merged\":true}")

(defn- document
  [body]
  {:contents       (.getBytes ^String body "UTF-8")
   :content-type   "application/json"
   :content-length (count (.getBytes ^String body "UTF-8"))})

(defn- plain-document
  [body]
  {:contents       (.getBytes ^String body "UTF-8")
   :content-type   "text/plain"
   :content-length (count (.getBytes ^String body "UTF-8"))})

(defn- precondition-ctx
  [preconditions]
  (assoc tc/ctx ::lrs-doc/preconditions preconditions))

(defn- params-for
  [{:keys [id-key params]} suffix]
  (update params id-key str "-" suffix))

(defn- get-body
  [lrs params]
  (some-> (lrsp/-get-document lrs tc/ctx tc/auth-ident params)
          :document
          :contents
          u/bytes->str))

(defn- precondition-failed?
  [result]
  (lrs-doc/precondition-failed? (:error result)))

(deftest document-write-preconditions-test
  (let [sys (component/start (support/test-system))
        lrs (:lrs sys)]
    (try
      (doseq [{:keys [label] :as resource} resource-cases]
        (testing label
          (let [stale-params (params-for resource "stale")
                match-params (params-for resource "match")
                same-params  (params-for resource "same")
                post-params  (params-for resource "post")
                put-new-params  (params-for resource "put-new")
                post-new-params (params-for resource "post-new")
                initial-etag (hash/sha-1 initial-body)
                match-ctx (precondition-ctx {:if-match #{initial-etag}})
                stale-ctx (precondition-ctx {:if-match #{"stale"}})
                create-ctx (precondition-ctx {:if-none-match :*})]
            (is (= {} (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                           stale-params
                                           (document initial-body)
                                           false)))
            (is (precondition-failed?
                 (lrsp/-set-document lrs stale-ctx tc/auth-ident
                                     stale-params
                                     (document replacement-body)
                                     false)))
            (is (= initial-body (get-body lrs stale-params))
                "stale PUT leaves the current representation unchanged")
            (is (precondition-failed?
                 (lrsp/-set-document lrs stale-ctx tc/auth-ident
                                     stale-params
                                     (document merge-body)
                                     true)))
            (is (= initial-body (get-body lrs stale-params))
                "stale POST leaves the current representation unchanged")

            (is (= {} (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                           match-params
                                           (document initial-body)
                                           false)))
            (is (= {} (lrsp/-set-document lrs match-ctx tc/auth-ident
                                           match-params
                                           (document replacement-body)
                                           false)))
            (is (= replacement-body (get-body lrs match-params))
                "matching PUT replaces the representation")

            (is (= {} (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                           same-params
                                           (document initial-body)
                                           false)))
            (is (= {} (lrsp/-set-document lrs match-ctx tc/auth-ident
                                           same-params
                                           (document initial-body)
                                           false)))
            (is (= initial-body (get-body lrs same-params))
                "matching same-content PUT succeeds")

            (is (= {} (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                           post-params
                                           (document initial-body)
                                           false)))
            (is (= {} (lrsp/-set-document lrs match-ctx tc/auth-ident
                                           post-params
                                           (document merge-body)
                                           true)))
            (is (= {"value" "initial" "merged" true}
                   (u/parse-json (get-body lrs post-params)))
                "matching POST merges from the observed representation")

            (is (= {} (lrsp/-set-document lrs create-ctx tc/auth-ident
                                           put-new-params
                                           (document initial-body)
                                           false)))
            (is (precondition-failed?
                 (lrsp/-set-document lrs create-ctx tc/auth-ident
                                     put-new-params
                                     (document replacement-body)
                                     false)))
            (is (= initial-body (get-body lrs put-new-params))
                "If-None-Match prevents PUT replacement")

            (is (= {} (lrsp/-set-document lrs create-ctx tc/auth-ident
                                           post-new-params
                                           (document initial-body)
                                           true)))
            (is (precondition-failed?
                 (lrsp/-set-document lrs create-ctx tc/auth-ident
                                     post-new-params
                                     (document merge-body)
                                     true)))
            (is (= initial-body (get-body lrs post-new-params))
                "If-None-Match prevents POST replacement"))))

      (testing "existing POST validation errors"
        (let [resource (last resource-cases)
              merge-params (params-for resource "invalid-merge")
              json-params  (params-for resource "invalid-json")]
          (is (= {} (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                         merge-params
                                         (plain-document "plain")
                                         false)))
          (let [result (lrsp/-set-document lrs tc/ctx tc/auth-ident
                                           merge-params
                                           (document merge-body)
                                           true)]
            (is (= ::lrs-doc/invalid-merge
                   (:type (ex-data (:error result)))))
            (is (= "plain" (get-body lrs merge-params))))
          (let [result (lrsp/-set-document
                        lrs tc/ctx tc/auth-ident
                        json-params
                        (document "not-json")
                        true)]
            (is (= ::lrs-doc/json-read-error
                   (:type (ex-data (:error result)))))
            (is (nil? (get-body lrs json-params))))))
      (finally
        (component/stop sys)))))
