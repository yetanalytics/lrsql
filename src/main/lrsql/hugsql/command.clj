(ns lrsql.hugsql.command
  "DB commands that utilize HugSql functions."
  (:require [clojure.data.json :as json]
            [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]))

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
    (f/insert-attachment! tx input)
    :statement-to-agent
    (f/insert-statement-to-agent! tx input)
    :statement-to-activity
    (f/insert-statement-to-activity! tx input)
    :state-document ; TODO
    nil
    :agent-profile-document ; TODO
    nil
    :activity-profile-document ; TODO
    nil))

(defn insert-inputs!
  "Insert a sequence of inputs into th DB. Return a seq of Statement IDs
   for successfully inserted Statements."
  [tx inputs]
  (->> inputs
       (map (partial insert-input! tx))
       doall
       (filter some?)
       (assoc {} :statement-ids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-json
  [jsn]
  (cond
    (string? jsn)
    (json/read-str jsn)
    (bytes? jsn) ; H2 returns JSON data as a byte array
    (json/read-str (String. jsn))))

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
