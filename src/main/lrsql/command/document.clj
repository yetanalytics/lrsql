(ns lrsql.command.document
  (:require [clojure.string :as cstr]
            [lrsql.functions :as f]
            [lrsql.util :as u]
            [lrsql.command.util :as cu]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Mutation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Insert document

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

;; Delete document(s)

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

;; Update document

(defn- mergeable-json
  "Checks that json is returned, and that it is mergeable"
  [{:keys [json]}]
  (when (and json
             (map? json))
    json))

(defn- doc->json
  "Given a document map with `:contents` property, return the parsed
   contents if valid, or nil otherwise."
  [doc]
  (mergeable-json
   (cu/wrapped-parse-json
    (:contents doc))))

(defn- json-content-type?
  [ctype]
  (cstr/starts-with? ctype "application/json"))

(defn- invalid-merge-error
  [old-doc input]
  {:error
   (ex-info "Invalid Merge"
            {:type :com.yetanalytics.lrs.xapi.document/invalid-merge
             :old-doc old-doc
             :new-doc input})})

(defn- json-read-error
  [input]
  {:error
   (ex-info "Invalid JSON object"
            {:type :com.yetanalytics.lrs.xapi.document/json-read-error
             :new-doc input})})

#_{:clj-kondo/ignore [:redundant-do]}
(defn- update-document!*
  "Common functionality for all cases in `update-document!`"
  [tx {new-ctype :content-type
       :as input} query-fn insert-fn! update-fn!]
  (let [query-in (dissoc input :last-modified :contents)]
    (if-some [{old-ctype :content_type
               :as old-doc} (query-fn tx query-in)]
      ;; We have a pre-existing document in the store
      ;; Only JSON documents can be stored
      (let [?old-json (and (json-content-type? old-ctype)
                           (doc->json old-doc))
            ?new-json (and (json-content-type? new-ctype)
                           (doc->json input))]
        (if (and ?old-json
                 ?new-json)
          (let [new-data  (->> (merge ?old-json ?new-json)
                               u/write-json
                               .getBytes)
                new-input (-> input
                              (assoc :contents new-data)
                              (assoc :content-length (count new-data)))]
            (do (update-fn! tx new-input)
                {}))
          (invalid-merge-error old-doc input)))
      ;; We don't have a pre-existing document - insert
      (if (and new-ctype
               (json-content-type? new-ctype))
        ;; XAPI-00314 - must check if doc contents are JSON if
        ;; content type is application/json
        (if-some [_ (doc->json input)]
          (do (insert-fn! tx input)
              {})
          (json-read-error input))
        ;; Regular data - directly insert
        (do (insert-fn! tx input)
            {})))))

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
    (cu/throw-invalid-table-ex "update-input!" input)))

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
    (let [{contents     :contents
           content-type :content_type
           content-len  :content_length
           state-id     :state_id
           profile-id   :profile_id
           updated      :last_modified} res]
      {:document
       {:contents       contents
        :content-length content-len
        :content-type   content-type
        :id             (or state-id profile-id)
        :updated        (u/time->str updated)}})))

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
