(ns lrsql.hugsql.command.document
  (:require [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.command.util :as cu]))

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
    (cu/throw-invalid-table-ex "insert-document!" input))
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
    (cu/throw-invalid-table-ex "delete-document!" input))
  {})

(defn delete-documents!
  "Delete multiple documents from the DB. Returns an empty map."
  [tx {:keys [table] :as input}]
  (case table
    :state-document
    (f/delete-state-documents! tx input)
    ;; Else
    (cu/throw-invalid-table-ex "delete-documents!" input))
  {})

(defn- update-document!*
  "Common functionality for all cases in `update-document!`"
  [tx input query-fn insert-fn! update-fn!]
  (let [query-in (dissoc input :last-modified :contents)
        old-data (query-fn tx query-in)]
    (if-some [old-doc (some->> old-data :contents)]
      (let [old-json (cu/wrapped-parse-json "stored document" old-doc)
            new-json (cu/wrapped-parse-json "new document" (:contents input))]
        (->> (merge old-json new-json)
             u/write-json
             .getBytes
             (assoc input :contents)
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
    (cu/throw-invalid-table-ex "update-input!" input))
  {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query-document
  "Query a single document from the DB. Returns either a map containing the
   document as a byte array, or nil if not found."
  [tx {:keys [table] :as input}]
  (when-some [res (case table
                    :state-document
                    (f/query-state-document tx input)
                    :agent-profile-document
                    (f/query-agent-profile-document tx input)
                    :activity-profile-document
                    (f/query-activity-profile-document tx input)
                    ;; Else
                    (cu/throw-invalid-table-ex "query-document" input))]
    (let [{contents   :contents
           state-id   :state_id
           profile-id :profile_id
           updated    :last_modified} res]
      {:contents       contents
       :content-length (count contents)
       :content-type   "application/octet-stream" ; TODO
       :id             (or state-id profile-id)
       :updated        updated})))

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
              (cu/throw-invalid-table-ex "query-document-ids" input))]
    {:document-ids (vec ids)}))
