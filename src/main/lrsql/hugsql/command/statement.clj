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
   Statement ID on success, nil for any other kind of input. May void
   previously-stored Statements."
  [tx {:keys [table] :as input}]
  (case table
    :statement
    ;; TODO: Query the statement by ID first; if IDs match, compare the payloads
    ;; to determine if the two statements are the same, in which case throw
    ;; an exception.
    (do (f/insert-statement! tx input)
        ;; Void statements
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
    :statement-to-statement
    (do (f/insert-statement-to-statement! tx input) nil)
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

(defn- query-res->statement
  [format ltags query-res]
  (-> query-res
      :payload
      u/parse-json
      (format-stmt format ltags)))

(defn- conform-attachment-res
  [{att-sha      :attachment_sha
    content-type :content_type
    length       :content_length
    contents     :contents}]
  {:sha2        att-sha
   :length      length
   :contentType content-type
   :content     contents})

(defn- query-one-statement
  "Query a single statement from the DB, using the `:statement-id` parameter."
  [tx input ltags]
  (let [{:keys [format attachments?] :or {format :exact}} input
        query-result (f/query-statement tx input)
        statement   (when query-result
                           (query-res->statement format ltags query-result))
        attachments (when (and statement attachments?)
                      (->> {:statement-id (get statement "id")}
                           (f/query-attachments tx)
                           conform-attachment-res))]
    (cond-> {}
      statement   (assoc :statement statement)
      attachments (assoc :attachments attachments))))

(defn- query-many-statements
  "Query potentially multiple statements from the DB."
  [tx input ltags]
  (let [{:keys [format limit attachments?]
         :or   {format :exact}} input
        input'        (if limit (update input :limit inc) input)
        query-results (f/query-statements tx input')
        next-cursor   (if (and limit
                               (= (inc limit) (count query-results)))
                        (-> query-results last :id u/uuid->str)
                        "")
        stmt-results  (map (partial query-res->statement format ltags)
                           (if (not-empty next-cursor)
                             (butlast query-results)
                             query-results))
        att-results   (if attachments?
                        (->> (doall (map (fn [stmt]
                                           (->> (get stmt "id")
                                                (assoc {} :statement-id)
                                                (f/query-attachments tx)))
                                         stmt-results))
                             (apply concat)
                             (map conform-attachment-res))
                        [])]
    {:statement-result {:statements stmt-results
                        :more       next-cursor}
     :attachments      att-results}))

(defn query-statements
  "Query statements from the DB. Return a map containing a singleton
   `:statement` if a statement ID is included in the query, or a
   `:statement-result` object otherwise. The map also contains `:attachments`
   to return any associated attachments. The `ltags` argument controls which
   language tag-value pairs are returned when `:format` is `:canonical`.
   Note that the `:more` property of `:statement-result` returned is a
   statement PK, NOT the full URL."
  [tx input ltags]
  (let [{:keys [statement-id]} input]
    (if statement-id
      (query-one-statement tx input ltags)
      (query-many-statements tx input ltags))))

(defn query-statement-refs
  "Query Statement References from the DB. In addition to the immediate
   references given by `:?statement-ref-id`, it returns ancestral
   references, i.e. not only the Statement referenced by `:?statement-ref-id`,
   but the Statement referenced by _that_, and so on. The return value
   is a lazy seq of maps with `:statement-id` and `:ancestor-id` properties,
   where `:statement-id` is the same as in `input`; these maps serve as
   additional inputs for `insert-statements!`."
  [tx input]
  (if-some [sref-id (:?statement-ref-id input)]
    (let [stmt-id (:statement-id input)]
      ;; Find ancestors of the referenced Statement, and make those
      ;; the ancestors of the referencing Statement.
      (->> (f/query-statement-ancestors tx {:descendant-id sref-id})
           (map (fn [{ancestor-id :ancestor_id}]
                  {:table         :statement-to-statement
                   :descendant-id stmt-id
                   :ancestor-id   ancestor-id}))
           (concat [{:table         :statement-to-statement
                     :descendant-id stmt-id
                     :ancestor-id   sref-id}])))
    (lazy-seq [])))
