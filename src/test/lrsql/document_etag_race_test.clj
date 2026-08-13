(ns lrsql.document-etag-race-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.util.hash :as hash]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
            [lrsql.util.document :as doc-util])
  (:import [java.io File]
           [java.sql DriverManager]))

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

(defn- delete-sqlite-files!
  [^File db-file]
  (doseq [path [(.getAbsolutePath db-file)
                (str (.getAbsolutePath db-file) "-wal")
                (str (.getAbsolutePath db-file) "-shm")]]
    (let [file (File. path)]
      (when (and (.exists file) (not (.delete file)))
        (.deleteOnExit file)))))

(defn- start-race-system!
  "Start the configured test system with enough independent connections for a
   transaction-level race. SQLite additionally needs a file-backed WAL database
   so a winner can commit while the observed transaction remains open."
  []
  (let [base-system (support/test-system)]
    (if (= "sqlite"
           (get-in base-system [:connection :config :database :db-type]))
      (let [db-file (doto (File/createTempFile "lrsql-etag-race-" ".db")
                      (.deleteOnExit))
            jdbc-url (str "jdbc:sqlite:" (.getAbsolutePath db-file))]
        (with-open [conn (DriverManager/getConnection jdbc-url)
                    stmt (.createStatement conn)]
          (.execute stmt "PRAGMA journal_mode=WAL"))
        (let [sys (component/start
                   (support/test-system
                    :conf-overrides
                    {[:connection :pool-maximum-size] 2
                     [:connection :database :db-jdbc-url]
                     jdbc-url}))]
          {:sys sys :sqlite-db-file db-file}))
      {:sys (component/start base-system)})))

(defn- stop-race-system!
  [{:keys [sys sqlite-db-file]}]
  (try
    (component/stop sys)
    (finally
      (when sqlite-db-file
        (delete-sqlite-files! sqlite-db-file)))))

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
    (let [original-preconditions-met? doc-util/preconditions-met?
          read-done                   (promise)
          release-read                (promise)
          read-count                  (atom 0)]
      (with-redefs [doc-util/preconditions-met?
                    (fn [& args]
                      (let [result (apply original-preconditions-met? args)]
                        ;; Pause only the stale request after LRSQL's
                        ;; authoritative database read and validation.
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
  (let [{:keys [sys] :as race-system} (start-race-system!)]
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
        (stop-race-system! race-system)))))

(deftest authoritative-precondition-handoff-test
  (let [sys (component/start (support/test-system))
        lrs-impl (:lrs sys)]
    (try
      (is (true? (lrsp/atomic-document-preconditions? lrs-impl)))
      (let [resource       (first resource-cases)
            put-id         "handoff-put"
            post-id        "handoff-post"
            delete-id      "handoff-delete"
            collection     (assoc-in resource
                                     [:query-params "activityId"]
                                     "https://example.org/etag-handoff/collection")
            collection-ids ["alpha" "zeta"]
            match-etag     (hash/sha-1 initial-body)
            match-header   {"If-Match" (str "\"" match-etag "\"")}
            observed       (atom [])]
        (doseq [document-id [put-id post-id delete-id]]
          (ensure-status! 204
                          (document-request resource
                                            :put
                                            document-id
                                            {"If-None-Match" "*"}
                                            initial-body)
                          "single document setup"))
        (doseq [state-id collection-ids]
          (ensure-status! 204
                          (document-request collection
                                            :put
                                            state-id
                                            {"If-None-Match" "*"}
                                            initial-body)
                          "collection setup"))
        (let [original-preconditions doc-util/preconditions]
          (with-redefs [lrs/get-document
                        (fn [& _]
                          (throw (ex-info "Unexpected preliminary document GET"
                                          {})))
                        lrs/get-document-ids
                        (fn [& _]
                          (throw (ex-info "Unexpected preliminary collection GET"
                                          {})))
                        doc-util/preconditions
                        (fn [ctx]
                          (let [preconditions (original-preconditions ctx)]
                            (swap! observed conj preconditions)
                            preconditions))]
            (ensure-status! 204
                            (document-request resource
                                              :put
                                              put-id
                                              match-header
                                              winner-body)
                            "conditional PUT")
            (ensure-status! 204
                            (document-request resource
                                              :post
                                              post-id
                                              match-header
                                              stale-body)
                            "conditional POST")
            (ensure-status! 204
                            (document-request resource
                                              :delete
                                              delete-id
                                              match-header
                                              nil)
                            "conditional single DELETE")
            (ensure-status!
             204
             (state-collection-request
              collection
              :delete
              {"If-Match"
               (str "\""
                    (doc-util/state-document-ids-etag collection-ids)
                    "\"")})
             "conditional collection DELETE")))
        (is (= [{:if-match #{match-etag}}
                {:if-match #{match-etag}}
                {:if-match #{match-etag}}
                {:if-match
                 #{(doc-util/state-document-ids-etag collection-ids)}}]
               @observed)
            "LRSQL receives each normalized precondition map unchanged"))
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
          read-count                  (atom 0)]
      (with-redefs [doc-util/preconditions-met?
                    (fn [& args]
                      (let [result (apply original-preconditions-met? args)]
                        ;; This hook is used only by LRSQL's authoritative
                        ;; validation, not by the preliminary LRS interceptor.
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
                    (case membership-change
                      :addition
                      [(document-request resource
                                         :put
                                         "new"
                                         nil
                                         winner-body)]

                      :removal
                      [(document-request resource
                                         :delete
                                         "zeta"
                                         nil
                                         nil)]

                      :recreation
                      [(document-request resource
                                         :delete
                                         "zeta"
                                         nil
                                         nil)
                       (document-request resource
                                         :put
                                         "zeta"
                                         nil
                                         winner-body)]))]
              (try
                ;; Let the membership operation and the collection deletion
                ;; establish whichever ordering the backend permits.
                (deliver release-read true)
                (let [delete-response (await! collection-delete
                                              "collection DELETE")
                      membership-responses
                      (await! membership-request "membership change")
                      final-response (state-collection-request resource
                                                               :get
                                                               nil)
                      final-ids      (u/parse-json (:body final-response)
                                                   :object? false)]
                  (is (every? #(= 204 (:status %)) membership-responses))
                  (is (contains? #{204 412} (:status delete-response))
                      "a backend retry may expose the membership change and return 412")
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
                          "a concurrent removal remains absent")
                      (is (= (if (= 412 (:status delete-response))
                               ["alpha"]
                               [])
                             final-ids)))

                    :recreation
                    (do
                      (is (some #{"zeta"} final-ids)
                          "a recreated logical document has a new physical key and survives")
                      (is (= winner-body
                             (:body (document-request resource
                                                      :get
                                                      "zeta"
                                                      nil
                                                      nil))))
                      (is (= (if (= 412 (:status delete-response))
                               ["alpha" "zeta"]
                               ["zeta"])
                             final-ids)))))
                (finally
                  (future-cancel membership-request))))
            (finally
              (deliver release-read true)
              (future-cancel collection-delete))))))))

(deftest state-collection-etag-precondition-is-atomic-test
  (let [sys (component/start (support/test-system))]
    (try
      (doseq [membership-change [:addition :removal :recreation]]
        (testing (str "concurrent State document "
                      (name membership-change))
          (run-state-collection-race! membership-change)))
      (finally
        (component/stop sys)))))
