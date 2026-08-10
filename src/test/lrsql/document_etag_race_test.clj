(ns lrsql.document-etag-race-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.util.hash :as hash]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(def endpoint
  "http://localhost:8080/xapi/activities/profile")

(def base-headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def initial-body "{\"value\":\"initial\"}")
(def winner-body  "{\"value\":\"winner\"}")
(def stale-body   "{\"value\":\"stale\"}")

(defn- request-options
  [document-id header body]
  (cond-> {:basic-auth  ["username" "password"]
           :headers     (cond-> base-headers header (merge header))
           :query-params
           {"activityId" "https://example.org/etag-race"
            "profileId"  document-id}
           :throw       false}
    body (assoc :body body)))

(defn- document-request
  [method document-id header body]
  ((case method
     :put    curl/put
     :post   curl/post
     :delete curl/delete
     :get    curl/get)
   endpoint
   (request-options document-id header body)))

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
  [{:keys [action document-id precondition]}]
  (let [if-match? (= :if-match precondition)
        header    (if if-match?
                    {"If-Match" (str "\"" (hash/sha-1 initial-body) "\"")}
                    {"If-None-Match" "*"})]
    (when if-match?
      (ensure-status! 204
                      (document-request :put
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
                (document-request action
                                  document-id
                                  header
                                  (when-not (= :delete action) stale-body)))]
          (try
            (await! read-done "stale request precondition read")
            ;; This request legitimately wins using the same precondition.
            (ensure-status! 204
                            (document-request :put
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
           (select-keys (document-request :get document-id nil nil)
                        [:status :body]))
        "the stale request must not modify the winning representation")))

(deftest document-etag-precondition-is-atomic-test
  (let [sys (component/start (support/test-system))]
    (try
      (doseq [[action precondition]
              [[:put    :if-match]
               [:post   :if-match]
               [:delete :if-match]
               [:put    :if-none-match]
               [:post   :if-none-match]]]
        (testing (str (name action) " with " (name precondition))
          (run-race! {:action       action
                      :document-id  (str (name action) "-" (name precondition))
                      :precondition precondition})))
      (finally
        (component/stop sys)))))
