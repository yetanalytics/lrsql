(ns lrsql.hugsql.command
  "DB commands that utilize HugSql functions."
  (:require [clojure.data.json :as json]
            [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-json
  [jsn]
  (cond
    (string? jsn)
    (json/read-str jsn)
    (bytes? jsn) ; H2 returns JSON data as a byte array
    (json/read-str (String. jsn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn insert-input!
  "Insert a new input into the DB. If the input is a Statement, return the
   Statement ID on success, nil for any other kind of input."
  [tx {:keys [table] :as input}]
  (case table
    :statement
    ;; TODO: Query the statement by ID first; if IDs match, compare the payloads
    ;; to determine if the two statements are the same, in which case throw
    ;; an exception.
    (do (f/insert-statement! tx input)
        ;; Void statements
        (when (:voiding? input) ; TODO test
          (f/void-statement! tx {:statement-id (:?statement-ref-id input)}))
        ;; Success! (Too bad H2 doesn't have INSERT...RETURNING)
        (u/uuid->str (:statement-id input)))
    :agent
    (let [input' (select-keys input [:agent-ifi])
          exists (f/query-agent-exists tx input')]
      (when-not exists (f/insert-agent! tx input)))
    :activity
    (let [input' (select-keys input [:activity-iri])
          exists  (f/query-activity-exists tx input')]
      (when-not exists (f/insert-activity! tx input)))
    :attachment
    (let [input' (select-keys input [:attachment-sha])
          exists (f/query-attachment-exists tx input')]
      (when-not exists (f/insert-attachment! tx input)))
    :statement-to-agent
    (f/insert-statement-to-agent! tx input)
    :statement-to-activity
    (f/insert-statement-to-activity! tx input)
    :statement-to-attachment
    (f/insert-statement-to-attachment! tx input)
    :state-document
    (do
      (f/insert-state-document! tx input)
      {})
    :agent-profile-document
    (do
      (f/insert-agent-profile-document! tx input)
      {})
    :activity-profile-document
    (do
      (f/insert-activity-profile-document! tx input)
      {})))

(defn insert-inputs!
  "Insert a sequence of inputs into th DB. Return a seq of Statement IDs
   for successfully inserted Statements."
  [tx inputs]
  (->> inputs
       (map (partial insert-input! tx))
       doall
       (filter some?)
       (assoc {} :statement-ids)))

(defn update-input!
  [tx {:keys [table] :as input}]
  (let [[query-fn update-fn!]
        (case table
          :state-document
          [f/query-state-document
           f/update-state-document!]
          :agent-profile-document
          [f/query-agent-profile-document
           f/update-agent-profile-document!]
          :activity-profile-document
          [f/query-activity-profile-document
           f/update-activity-profile-document!]
          :else
          (throw (ex-info "`update-input!` is not supported for this table type"
                          {:kind  ::invalid-table-type
                           :input input})))
        old-doc
        (some->> (select-keys
                  input
                  [:state-id :agent-ifi :activity-iri :?registration])
                 (query-fn tx)
                 :document)
        old-json-doc
        (try (parse-json old-doc)
             (catch Exception _
               (throw (ex-info "Cannot merge non-JSON document"
                               {:kind ::non-json-document
                                :input input
                                :document old-doc}))))
        input'
        (update input :document (fn [new-doc] (merge old-doc new-doc)))]
    (update-fn! tx input')))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- conform-attachment-res
  [{att-sha      :attachment_sha
    content-type :content_type
    length       :content_length
    content      :payload}]
  {:sha2        att-sha
   :length      length
   :contentType content-type
   :content     content})

(defn query-statement-input
  "Query Statements from the DB. Return a singleton Statement or nil if
   a Statement ID is included in params, a StatementResult object otherwise."
  [tx input]
  (let [stmt-res  (->> input
                       (f/query-statement tx)
                       (map #(-> % :payload parse-json)))
        att-res   (if (:attachments? input)
                    (->> (doall (map #(->> (get % "id")
                                           (assoc {} :statement-id)
                                           (f/query-attachments tx))
                                     stmt-res))
                         (apply concat)
                         (map conform-attachment-res))
                    [])]
    (if (:statement-id input)
      ;; Singleton statement
      (cond-> {}
        (not-empty stmt-res)
        (assoc :statement (first stmt-res))
        (and (not-empty stmt-res) (not-empty att-res))
        (assoc :attachments att-res))
      ;; Multiple statements
      ;; TODO: Return IRI if more statements can be queried
      {:statement-result {:statements (vec stmt-res)
                          :more       ""}
       :attachments      att-res})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Documents
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query-document
  "Query a single document from the DB. Returns either a map containing the
   document as a byte array, or nil if not found."
  [tx {:keys [table] :as input}]
  (let [res (case table
              :state-document
              (f/query-state-document tx input)
              :agent-profile-document
              (f/query-agent-profile-document tx input)
              :activity-profile-document
              (f/query-activity-profile-document tx input))]
    {:contents       (-> res :document)
     :content-length (-> res :document count)
     :content-type   "application/octet-stream" ; TODO
     :id             (or (:state_id res) (:profile_id res))
     :updated        (:last_modified res)}))

;; TODO: The LRS should also return last modified info.
;; However, this is not supported in Milt's LRS spec.
(defn query-document-ids
  "Query multiple document IDs from the DB. Returns a map containing the
   vector of IDs."
  [tx {:keys [table] :as input}]
  (let [ids (case table
              :state-document
              (->> input
                   (f/query-state-document-ids tx)
                   (map :state_id))
              :agent-profile-document
              (->> input
                   (f/query-agent-profile-document-ids tx)
                   (map :profile_id))
              :activity-profile-document
              (->> input
                   (f/query-activity-profile-document-ids tx)
                   (map :profile_id)))]
    {:document-ids (vec ids)}))
