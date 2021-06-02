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

(defn- mergeable-json
  "Checks that json is returned, and that it is mergeable"
  [{:keys [json]}]
  (when (and json
             (map? json))
    json))

(defn- update-document!*
  "Common functionality for all cases in `update-document!`"
  [tx {new-ctype :content-type
       :as input} query-fn insert-fn! update-fn!]
  (let [query-in (dissoc input :last-modified :contents)]
    (if-let [{old-ctype :content_type
              :as old-doc} (query-fn tx query-in)]
      (if (every? ;; an attempted merge should be json-json
           #(.startsWith ^String % "application/json")
           [old-ctype
            new-ctype])
        (let [?old-json (mergeable-json
                         (cu/wrapped-parse-json
                          "stored document"
                          (:contents old-doc)))

              ?new-json (mergeable-json
                         (cu/wrapped-parse-json
                          "new document"
                          (:contents input)))]
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
            {:error
             (ex-info "Invalid Merge"
                      {:type :com.yetanalytics.lrs.xapi.document/invalid-merge
                       :old-doc old-doc
                       :new-doc input})}))
        ;; currently this does not happen
        ;; a content type always seems to be passed in even if one is not included
        {:error
         (ex-info "Invalid Merge"
                  {:type :com.yetanalytics.lrs.xapi.document/invalid-merge
                   :old-doc old-doc
                   :new-doc input})})
      ;; XAPI-00314
      (if-let [good-json (mergeable-json
                          (cu/wrapped-parse-json
                           "new document"
                           (:contents input)))]
        (let [new-data (->> good-json
                            u/write-json
                            .getBytes)
              new-input (-> input
                            (assoc :contents new-data)
                            (assoc :content-length (count new-data)))]
          (do (insert-fn! tx new-input)
              {}))
        {:error
         (ex-info "Invalid JSON object"
                  {:type :com.yetanalytics.lrs.xapi.document/json-read-error
                   :new-doc input})}))))

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
