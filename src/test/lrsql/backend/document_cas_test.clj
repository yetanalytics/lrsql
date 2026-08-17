(ns lrsql.backend.document-cas-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.backend.protocol :as bp]
            [lrsql.backend.result :as br]
            [lrsql.ops.command.document :as command]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
            [lrsql.util.document :as document-util]
            [next.jdbc :as jdbc])
  (:import [java.nio.charset StandardCharsets]
           [java.util UUID]))

(use-fixtures :once support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(defn- utf8-bytes
  [s]
  (.getBytes ^String s StandardCharsets/UTF_8))

(defn- document-input
  [table contents]
  (merge
   {:table          table
    :primary-key    (UUID/randomUUID)
    :last-modified  (u/current-time)
    :content-type   "application/json"
    :content-length (count contents)
    :contents       contents}
   (case table
     :state-document
     {:state-id     "state-id"
      :activity-iri "https://example.org/activity"
      :agent-ifi    "mbox::mailto:test@example.org"
      :registration nil}

     :agent-profile-document
     {:profile-id "agent-profile-id"
      :agent-ifi  "mbox::mailto:test@example.org"}

     :activity-profile-document
     {:profile-id   "activity-profile-id"
      :activity-iri "https://example.org/activity"})))

(defn- document-ops
  [table]
  (case table
    :state-document
    {:insert bp/-insert-state-document-if-absent!
     :update bp/-update-state-document-if-contents!
     :delete bp/-delete-state-document-if-contents!
     :query  bp/-query-state-document}

    :agent-profile-document
    {:insert bp/-insert-agent-profile-document-if-absent!
     :update bp/-update-agent-profile-document-if-contents!
     :delete bp/-delete-agent-profile-document-if-contents!
     :query  bp/-query-agent-profile-document}

    :activity-profile-document
    {:insert bp/-insert-activity-profile-document-if-absent!
     :update bp/-update-activity-profile-document-if-contents!
     :delete bp/-delete-activity-profile-document-if-contents!
     :query  bp/-query-activity-profile-document}))

(defn- missing-document-input
  [{:keys [table] :as input}]
  (case table
    :state-document            (assoc input :state-id "missing-state-id")
    :agent-profile-document    (assoc input :profile-id "missing-agent-profile-id")
    :activity-profile-document (assoc input :profile-id "missing-activity-profile-id")))

(deftest document-content-cas-primitives-test
  (let [sys (component/start (support/test-system))]
    (try
      (testing "affected-row normalization"
        (is (false? (br/affected->applied? 0)))
        (is (true? (br/affected->applied? 1)))
        (is (true? (br/affected->applied? {:next.jdbc/update-count 1})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Unexpected affected-row count"
                              (br/affected->applied? 2))))
      (let [bk (:backend sys)
            ds (get-in sys [:lrs :connection :conn-pool])]
        (doseq [table [:state-document
                       :agent-profile-document
                       :activity-profile-document]]
          (testing (name table)
            (let [{:keys [insert update delete query]} (document-ops table)
                  initial-contents (utf8-bytes "{\"value\":\"initial\"}")
                  stale-contents   (utf8-bytes "{\"value\":\"stale\"}")
                  winner-contents  (utf8-bytes "{\"value\":\"winner\"}")
                  initial-input    (document-input table initial-contents)
                  duplicate-input  (assoc initial-input
                                          :primary-key (UUID/randomUUID)
                                          :contents stale-contents
                                          :content-length (count stale-contents))
                  winner-input     (assoc initial-input
                                          :contents winner-contents
                                          :content-length (count winner-contents)
                                          :last-modified
                                          (u/millis->time
                                           (+ 1000
                                              (u/time->millis
                                               (:last-modified initial-input)))))]
              (jdbc/with-transaction [tx ds]
                (is (true? (insert bk tx initial-input))
                    "the absent logical key is inserted")
                (is (false? (insert bk tx duplicate-input))
                    "a duplicate logical key is not inserted")
                (is (= (seq initial-contents)
                       (seq (:contents (query bk tx initial-input))))
                    "the duplicate insert leaves the original contents")

                (is (false? (update bk tx
                                    (assoc winner-input
                                           :expected-contents stale-contents)))
                    "an update with stale observed contents is not applied")
                (is (false? (update bk tx
                                    (-> winner-input
                                        missing-document-input
                                        (assoc :expected-contents
                                               initial-contents))))
                    "an update for a missing logical key is not applied")
                (is (true? (update bk tx
                                   (assoc winner-input
                                          :expected-contents initial-contents)))
                    "an update with current observed contents is applied")
                (is (true? (update bk tx
                                   (assoc winner-input
                                          :expected-contents winner-contents)))
                    "a same-content update still matches the observed contents")

                (is (false? (delete bk tx
                                    (assoc winner-input
                                           :expected-contents initial-contents)))
                    "a delete with stale observed contents is not applied")
                (is (false? (delete bk tx
                                    (-> winner-input
                                        missing-document-input
                                        (assoc :expected-contents
                                               winner-contents))))
                    "a delete for a missing logical key is not applied")
                (is (true? (delete bk tx
                                   (assoc winner-input
                                          :expected-contents winner-contents)))
                    "a delete with current observed contents is applied")
                (is (nil? (query bk tx initial-input))
                    "the matching delete removes the document"))))))
      (finally
        (component/stop sys)))))

(defn- state-document-input
  [collection-input state-id]
  (merge collection-input
         {:table          :state-document
          :primary-key    (UUID/randomUUID)
          :state-id       state-id
          :last-modified  (u/current-time)
          :content-type   "application/json"
          :content-length 2
          :contents       (utf8-bytes "{}")}))

(deftest state-collection-observed-primary-key-primitives-test
  (let [sys (component/start (support/test-system))]
    (try
      (let [bk         (:backend sys)
            ds         (get-in sys [:lrs :connection :conn-pool])
            collection {:activity-iri "https://example.org/observed-keys"
                        :agent-ifi    "mbox::mailto:observed@example.org"
                        :registration nil}]
        (testing "the backend primitive deletes only observed, in-scope keys"
          (let [observed   (state-document-input collection "observed")
                unobserved (state-document-input collection "unobserved")
                other      (state-document-input
                            (assoc collection
                                   :activity-iri
                                   "https://example.org/other-collection")
                            "other")]
            (jdbc/with-transaction [tx ds]
              (doseq [input [observed unobserved other]]
                (bp/-insert-state-document! bk tx input))
              (bp/-delete-state-documents-by-primary-keys!
               bk tx
               (assoc collection
                      :primary-keys [(:primary-key observed)
                                     (:primary-key other)
                                     (UUID/randomUUID)]))
              (is (nil? (bp/-query-state-document bk tx observed)))
              (is (some? (bp/-query-state-document bk tx unobserved))
                  "an unobserved row in the collection survives")
              (is (some? (bp/-query-state-document bk tx other))
                  "a supplied key outside the collection scope survives"))))

        (testing "more than 500 observed keys are batched atomically"
          (let [batch-collection
                (assoc collection
                       :activity-iri "https://example.org/batched-keys")
                inputs (mapv #(state-document-input
                               batch-collection
                               (format "state-%03d" %))
                             (range 501))
                ids    (mapv :state-id inputs)
                ctx    {::lrs-doc/preconditions
                        {:if-match
                         #{(document-util/state-document-ids-etag ids)}}}]
            (jdbc/with-transaction [tx ds]
              (doseq [input inputs]
                (bp/-insert-state-document! bk tx input))
              (is (= 501
                     (count (bp/-query-state-document-ids
                             bk tx
                             (assoc batch-collection
                                    :table :state-document))))
                  "all rows are visible at the collection ordering point")
              (is (= {}
                     (command/delete-documents-cas!
                      bk tx ctx
                      (assoc batch-collection :table :state-document))))
              (is (empty? (bp/-query-state-document-ids
                           bk tx
                           (assoc batch-collection
                                  :table :state-document))))))))
      (finally
        (component/stop sys)))))
