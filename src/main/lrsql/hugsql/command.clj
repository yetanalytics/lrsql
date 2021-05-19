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

(defmacro throw-invalid-table-ex
  "Throw an exception with the following error data:
     :kind     ::invalid-table
     :table    <table name>
     :input    `input`
     :fn-name  `fn-name`"
  [fn-name input]
  (let [table-kw#   (:table input)
        table-name# (when table-kw# (name table-kw#))]
    `(throw
      (ex-info (format "`%s` is not supported for table type `%s`"
                       ~fn-name
                       ~table-name#)
               {:kind    ::invalid-table
                :table   ~table-kw#
                :input   ~input
                :fn-name ~fn-name}))))

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
        ;; CHECK: Throw exception on invalid voiding?
        (when (:voiding? input)
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
    (f/insert-attachment! tx input)
    :statement-to-agent
    (f/insert-statement-to-agent! tx input)
    :statement-to-activity
    (f/insert-statement-to-activity! tx input)
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
  "Insert a sequence of inputs into th DB. Return the following map:
   
   {:statement-id <seq of statement IDs>}"
  [tx inputs]
  (->> inputs
       (map (partial insert-input! tx))
       doall
       (filter some?)
       (assoc {} :statement-ids)))

(defn- update-input!*
  [tx input query-fn insert-fn! update-fn!]
  (if-some [old-doc (some->> (select-keys
                              input
                              [:state-id :agent-ifi :activity-iri :?registration])
                             (query-fn tx)
                             :document)]
    (let [old-json
          (try (parse-json old-doc)
               (catch Exception _
                 (throw (ex-info "Cannot merge into non-JSON document"
                                 {:kind    ::non-json-document
                                  :input   input
                                  :old-doc old-doc}))))
          new-json
          (try (parse-json (:document input))
               (catch Exception _
                 (throw (ex-info "Cannot merge in new non-JSON document"
                                 {:kind    ::non-json-document
                                  :input   input
                                  :old-doc old-doc}))))]
      (->> (merge old-json new-json)
           json/write-str
           .getBytes
           (assoc input :document)
           (update-fn! tx))
      {})
    (do
      (insert-fn! tx input)
      {})))

(defn update-input!
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (update-input!* tx
                    input
                    f/query-state-document
                    f/insert-state-document!
                    f/update-state-document!)
    :agent-profile-document
    (update-input!* tx
                    input
                    f/query-agent-profile-document
                    f/insert-agent-profile-document!
                    f/update-agent-profile-document!)
    :activity-profile-document
    (update-input!* tx
                    input
                    f/query-activity-profile-document
                    f/insert-activity-profile-document!
                    f/update-activity-profile-document!)
    ;; Else
    (throw-invalid-table-ex "update-input!" input)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- conform-attachment-res
  [{att-sha      :attachment_sha
    content-type :content_type
    length       :content_length
    content      :content}]
  {:sha2        att-sha
   :length      length
   :contentType content-type
   :content     content})

(defn query-statement-input
  "Query Statements from the DB. Return the following on a singleton Statement
   query (i.e. if a Statement ID is included in params):

   {:statement   <queried statement>
    :attachments <seq of attachments>}
   
   and the following if multiple Statements are queried:
   {:statement-result {:statements <seq of queried statements>}
                       :more       <url for additional queries>}
    :attachments      <seq of attachments>}"
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
              (f/query-activity-profile-document tx input)
              ;; Else
              (throw-invalid-table-ex "query-document" input))]
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
                   (map :profile_id))
              ;; Else
              (throw-invalid-table-ex "query-document-ids" input))]
    {:document-ids (vec ids)}))

(defn delete-document!
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (f/delete-state-document! tx input)
    :agent-profile-document
    (f/delete-agent-profile-document! tx input)
    :activity-profile-document
    (f/delete-activity-profile-document! tx input)
    ;; Else
    (throw-invalid-table-ex "delete-document!" input))
  {})
