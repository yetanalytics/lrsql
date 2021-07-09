(ns lrsql.ops.query.document
  (:require [clojure.spec.alpha :as s]
            [lrsql.functions :as f]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.document :as ds]
            [lrsql.util :as u]
            [lrsql.ops.util :refer [throw-invalid-table-ex]]))

(s/fdef query-document
  :args (s/cat :tx transaction? :input ds/document-input-spec)
  :ret any? #_ds/document-query-res-spec)

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
                    (throw-invalid-table-ex "query-document" input))]
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

(s/fdef query-document-ids
  :args (s/cat :tx transaction? :input ds/document-ids-query-spec)
  :ret ds/document-ids-query-res-spec)

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
