(ns lrsql.hugsql.command
  "DB commands that utilize HugSql functions."
  (:require [clojure.data.json :as json]
            [com.yetanalytics.lrs.xapi.agents :as agnt]
            [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-json
  "Parse `data` into JSON format."
  [data]
  (cond
    (string? data)
    (json/read-str data)
    (bytes? data) ; H2 returns JSON data as a byte array
    (json/read-str (String. data))))

(defmacro wrapped-parse-json
  "Wraps `parse-json` in a try-catch block, throwing ExceptionInfo containing
   the description `data-type` on failure."
  [data-type data]
  `(try (parse-json ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse %s as JSON!" ~data-type)
                          {:kind ::non-json-document
                           :type ~data-type
                           :data ~data})))))

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
;; Statement Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement-input!
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
    (do (let [input' (select-keys input [:agent-ifi])
              exists (f/query-agent-exists tx input')]
          (when-not exists (f/insert-agent! tx input)))
        nil)
    :activity
    (do (let [input' (select-keys input [:activity-iri])
              exists (f/query-activity-exists tx input')]
          (when-not exists (f/insert-activity! tx input)))
        nil)
    :attachment
    (do (f/insert-attachment! tx input) nil)
    :statement-to-agent
    (do (f/insert-statement-to-agent! tx input) nil)
    :statement-to-activity
    (do (f/insert-statement-to-activity! tx input) nil)
    ;; Else
    (throw-invalid-table-ex "insert-statement!" input)))

(defn insert-statements!
  "Insert one or more statements into the DB by inserting a sequence of inputs.
   Return a map between `:statement-ids` and a coll of statement IDs."
  [tx inputs]
  (->> inputs
       (map (partial insert-statement-input! tx))
       doall
       (filter some?)
       (assoc {} :statement-ids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
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

(defn query-statements
  "Query statements from the DB. Return a map containing a singleton
   `:statement` if a statement ID is included in the query, or a
   `:statement-result` object otherwise. The map also contains `:attachments`
   to return any associated attachments."
  [tx input]
  (let [stmt-res  (->> input
                       (f/query-statements tx)
                       (map #(->> % :payload (wrapped-parse-json "statement"))))
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
;; Statement Object Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query-agent
  [tx input]
  (if-some [{:keys [payload]} (f/query-agent tx input)]
    {:person (->> payload (wrapped-parse-json "agent") agnt/person)}
    (throw (ex-info "Agent not found" {:kind  ::no-agent
                                       :input input}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Mutation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn insert-document!
  "Insert a new document into the DB. Returns an empty map."
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (f/insert-state-document! tx input)
    :agent-profile-document
    (f/insert-agent-profile-document! tx input)
    :activity-profile-document
    (f/insert-activity-profile-document! tx input)
    ;; Else
    (throw-invalid-table-ex "insert-document!" input))
  {})

(defn delete-document!
  "Delete a single document from the DB. Returns an empty map."
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

(defn delete-documents!
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (f/delete-state-documents! tx input)
    ;; Else
    (throw-invalid-table-ex "delete-documents!" input))
  {})

(defn- update-document!*
  "Common functionality for all cases in `update-document!`"
  [tx input query-fn insert-fn! update-fn!]
  (let [query-keys [:state-id :agent-ifi :activity-iri :?registration]
        old-data   (query-fn tx (select-keys input query-keys))]
    (if-some [old-doc (some->> old-data :document)]
      (let [old-json (wrapped-parse-json "stored document" old-doc)
            new-json (wrapped-parse-json "new document" (:document input))]
        (->> (merge old-json new-json)
             json/write-str
             .getBytes
             (assoc input :document)
             (update-fn! tx)))
      (insert-fn! tx input))))

(defn update-document!
  "Update the document given by `input` if found, inserts a new document
   otherwise. Assumes that both the old and new document are in JSON format.
   Returns an empty map."
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (update-document!* tx
                       input
                       f/query-state-document
                       f/insert-state-document!
                       f/update-state-document!)
    :agent-profile-document
    (update-document!* tx
                       input
                       f/query-agent-profile-document
                       f/insert-agent-profile-document!
                       f/update-agent-profile-document!)
    :activity-profile-document
    (update-document!* tx
                       input
                       f/query-activity-profile-document
                       f/insert-activity-profile-document!
                       f/update-activity-profile-document!)
    ;; Else
    (throw-invalid-table-ex "update-input!" input))
  {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Query
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
