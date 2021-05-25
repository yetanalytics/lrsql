(ns lrsql.hugsql.command.statement
  (:require
   [com.yetanalytics.lrs.xapi.statements :as ss]
   [lrsql.hugsql.functions :as f]
   [lrsql.hugsql.util :as u]
   [lrsql.hugsql.command.util :as cu]))

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
    :actor
    (do (let [input' (select-keys input [:actor-ifi])
              exists (f/query-actor-exists tx input')]
          (when-not exists (f/insert-actor! tx input)))
        nil)
    :activity
    (do (let [input' (select-keys input [:activity-iri])
              exists (f/query-activity-exists tx input')]
          (when-not exists (f/insert-activity! tx input)))
        nil)
    :attachment
    (do (f/insert-attachment! tx input) nil)
    :statement-to-actor
    (do (f/insert-statement-to-actor! tx input) nil)
    :statement-to-activity
    (do (f/insert-statement-to-activity! tx input) nil)
    ;; Else
    (cu/throw-invalid-table-ex "insert-statement!" input)))

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

(defn- format-stmt
  [statement format ltags]
  (case format
    :ids
    (ss/format-statement-ids statement)
    :canonical
    (ss/format-canonical statement ltags)
    :exact
    statement
    ;; else
    (throw (ex-info "Unknown format type"
                    {:kind   ::unknown-format-type
                     :format format}))))

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
   to return any associated attachments. The `ltags` argument controls which
   language tag-value pairs are returned when `:format` is `:canonical`.
   Note that the `:more` property of `:statement-result` returned is a
   statement PK, NOT the full URL."
  [tx input ltags]
  (let [{:keys [format limit]
         :or {format :exact}} input
        ;; If `limit` is present, we need to query one more in order to
        ;; know if there are additional results that can be queried later.
        input'        (if limit (update input :limit inc) input)
        query-results (->> input'
                           (f/query-statements tx))
        ;; We can use the statement PKs as cursors since they are always
        ;; sequential (as SQUUIDs) and unique.
        next-cursor   (when (and limit
                                 (= (inc limit) (count query-results)))
                        (-> query-results last :id u/uuid->str))
        stmt-results  (map (fn [query-res]
                             (-> query-res
                                 :payload
                                 u/parse-json
                                 (format-stmt format ltags)))
                           (if next-cursor
                             (butlast query-results)
                             query-results))
        att-results   (if (:attachments? input)
                        (->> (doall (map (fn [stmt]
                                           (->> (get stmt "id")
                                                (assoc {} :statement-id)
                                                (f/query-attachments tx)))
                                         stmt-results))
                             (apply concat)
                             (map conform-attachment-res))
                        [])]
    (if (:statement-id input)
      ;; Singleton statement
      (cond-> {}
        (not-empty stmt-results)
        (assoc :statement (first stmt-results))
        (and (not-empty stmt-results) (not-empty att-results))
        (assoc :attachments att-results))
      ;; Multiple statements
      {:statement-result {:statements stmt-results
                          :more       (if next-cursor next-cursor "")}
       :attachments      att-results})))
