(ns lrsql.document-etag-race-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.util.hash :as hash]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(def agent-param
  "{\"mbox\":\"mailto:etag-race@example.org\"}")

(def resource-cases
  [{:label             "State"
    :endpoint          "http://localhost:8080/xapi/activities/state"
    :document-id-param "stateId"
    :query-params      {"activityId" "https://example.org/etag-race"
                        "agent"      agent-param}}
   {:label             "Agent Profile"
    :endpoint          "http://localhost:8080/xapi/agents/profile"
    :document-id-param "profileId"
    :query-params      {"agent" agent-param}}
   {:label             "Activity Profile"
    :endpoint          "http://localhost:8080/xapi/activities/profile"
    :document-id-param "profileId"
    :query-params      {"activityId" "https://example.org/etag-race"}}])

(def base-headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def initial-body "{\"value\":\"initial\"}")
(def winner-body  "{\"value\":\"winner\"}")
(def stale-body   "{\"value\":\"stale\"}")

(defn- request-options
  [{:keys [document-id-param query-params]} document-id header body]
  (cond-> {:basic-auth  ["username" "password"]
           :headers     (cond-> base-headers header (merge header))
           :query-params (assoc query-params document-id-param document-id)
           :throw       false}
    body (assoc :body body)))

(defn- document-request
  [{:keys [endpoint] :as resource} method document-id header body]
  ((case method
     :put    curl/put
     :post   curl/post
     :delete curl/delete
     :get    curl/get)
   endpoint
   (request-options resource document-id header body)))

(defn- await!
  [pending label]
  (let [result (deref pending 10000 ::timeout)]
    (when (= ::timeout result)
      (throw (ex-info (str "Timed out waiting for " label) {:label label})))
    result))

(defn- ensure-status!
  [expected response label]
  (when-not (= expected (:status response))
    (throw (ex-info (str "Unexpected response from " label)
                    {:expected expected
                     :actual   response}))))

(defn- run-race!
  [{:keys [resource action document-id precondition]}]
  (let [if-match? (= :if-match precondition)
        header    (if if-match?
                    {"If-Match" (str "\"" (hash/sha-1 initial-body) "\"")}
                    {"If-None-Match" "*"})]
    (when if-match?
      (ensure-status! 204
                      (document-request resource
                                        :put
                                        document-id
                                        {"If-None-Match" "*"}
                                        initial-body)
                      "initial document PUT"))
    (let [original-get lrs/get-document
          read-done    (promise)
          release-read (promise)
          read-count   (atom 0)]
      (with-redefs [lrs/get-document
                    (fn [& args]
                      (let [result (apply original-get args)]
                        ;; Pause only the stale request, after its database read
                        ;; has completed but before its precondition is applied.
                        (when (= 1 (swap! read-count inc))
                          (deliver read-done true)
                          (await! release-read "stale request release"))
                        result))]
        (let [stale-request
              (future
                (document-request resource
                                  action
                                  document-id
                                  header
                                  (when-not (= :delete action) stale-body)))]
          (try
            (await! read-done "stale request precondition read")
            ;; This request legitimately wins using the same precondition.
            (ensure-status! 204
                            (document-request resource
                                              :put
                                              document-id
                                              header
                                              winner-body)
                            "winning document PUT")
            (deliver release-read true)
            (is (= 412 (:status (await! stale-request "stale request")))
                "the stale request must be rejected")
            (finally
              (deliver release-read true)
              (future-cancel stale-request))))))
    (is (= {:status 200 :body winner-body}
           (select-keys (document-request resource
                                          :get
                                          document-id
                                          nil
                                          nil)
                        [:status :body]))
        "the stale request must not modify the winning representation")))

(deftest document-etag-precondition-is-atomic-test
  (let [sys (component/start (support/test-system))]
    (try
      (doseq [{:keys [label] :as resource} resource-cases
              [action precondition]
              [[:put    :if-match]
               [:post   :if-match]
               [:delete :if-match]
               [:put    :if-none-match]
               [:post   :if-none-match]]]
        (testing (str label ": " (name action) " with " (name precondition))
          (run-race! {:resource     resource
                      :action       action
                      :document-id  (str (name action)
                                         "-"
                                         (name precondition))
                      :precondition precondition})))
      (finally
        (component/stop sys)))))
