(ns lrsql.document-etag-race-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.util.hash :as hash]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
            [lrsql.util.document :as doc-util]))

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

(defn- state-collection-request
  [{:keys [endpoint query-params]} method header]
  ((case method
     :delete curl/delete
     :get    curl/get)
   endpoint
   {:basic-auth   ["username" "password"]
    :headers      (cond-> base-headers header (merge header))
    :query-params query-params
    :throw        false}))

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

(defn- run-state-collection-race!
  [membership-change]
  (let [suffix   (name membership-change)
        resource (-> (first resource-cases)
                     (assoc-in [:query-params "activityId"]
                               (str "https://example.org/etag-race/collection/"
                                    suffix)))
        initial-ids ["alpha" "zeta"]]
    (doseq [state-id initial-ids]
      (ensure-status! 204
                      (document-request resource
                                        :put
                                        state-id
                                        {"If-None-Match" "*"}
                                        initial-body)
                      "initial State document PUT"))
    (let [header {"If-Match"
                  (str "\""
                       (doc-util/state-document-ids-etag initial-ids)
                       "\"")}
          original-preconditions-met? doc-util/preconditions-met?
          collection-read             (promise)
          release-read                (promise)
          membership-started          (promise)
          read-count                  (atom 0)]
      (with-redefs [doc-util/preconditions-met?
                    (fn [& args]
                      (let [result (apply original-preconditions-met? args)]
                        ;; Pause the collection request after its authoritative
                        ;; transaction has read and validated membership.
                        (when (= 1 (swap! read-count inc))
                          (deliver collection-read true)
                          (await! release-read "collection DELETE release"))
                        result))]
        (let [collection-delete
              (future (state-collection-request resource :delete header))]
          (try
            (await! collection-read "collection DELETE validation")
            (let [membership-request
                  (future
                    (deliver membership-started true)
                    (case membership-change
                      :addition
                      (document-request resource
                                        :put
                                        "new"
                                        {"If-None-Match" "*"}
                                        winner-body)

                      :removal
                      (document-request resource
                                        :delete
                                        "zeta"
                                        nil
                                        nil)))]
              (try
                (await! membership-started "membership-changing request")
                (deliver release-read true)
                (let [delete-response   (await! collection-delete
                                                "collection DELETE")
                      membership-response
                      (await! membership-request "membership change")
                      final-response (state-collection-request resource
                                                               :get
                                                               nil)
                      final-ids      (u/parse-json (:body final-response)
                                                   :object? false)]
                  (is (= 204 (:status membership-response)))
                  (is (contains? #{204 412} (:status delete-response))
                      "the collection DELETE either precedes the change or rejects its stale ETag")
                  (is (= 200 (:status final-response)))
                  (case membership-change
                    :addition
                    (do
                      (is (some #{"new"} final-ids)
                          "a concurrent addition is never deleted")
                      (is (= (if (= 412 (:status delete-response))
                               ["alpha" "new" "zeta"]
                               ["new"])
                             final-ids)))

                    :removal
                    (do
                      (is (not-any? #{"zeta"} final-ids)
                          "the concurrent removal is preserved")
                      (is (= (if (= 412 (:status delete-response))
                               ["alpha"]
                               [])
                             final-ids)))))
                (finally
                  (future-cancel membership-request))))
            (finally
              (deliver release-read true)
              (future-cancel collection-delete))))))))

(deftest state-collection-etag-precondition-is-atomic-test
  (let [sys (component/start (support/test-system))]
    (try
      (testing "concurrent State document addition"
        (run-state-collection-race! :addition))
      (testing "concurrent State document removal"
        (run-state-collection-race! :removal))
      (finally
        (component/stop sys)))))
