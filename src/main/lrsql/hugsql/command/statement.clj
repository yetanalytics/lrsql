(ns lrsql.hugsql.command.statement
  (:require [lrsql.hugsql.functions :as f]
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
                       (map #(->> % :payload u/parse-json)))
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
