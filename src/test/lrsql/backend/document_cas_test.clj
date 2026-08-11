(ns lrsql.backend.document-cas-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [lrsql.backend.protocol :as bp]
            [lrsql.backend.result :as br]
            [lrsql.test-support :as support]
            [lrsql.util :as u]
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
