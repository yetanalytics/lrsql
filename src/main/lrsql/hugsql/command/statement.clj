(ns lrsql.hugsql.command.statement
  (:require [clojure.tools.logging :as log]
            [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.util.activity :as ua]
            [lrsql.hugsql.util.statement :as us]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- prepare-input
  "Prepare the input for insertion. In particular, convert the payload into a
   JSON string."
  [input]
  (update input :payload u/write-json))

(defn- insert-statement-input!
  [tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input
        exists (f/query-statement-exists tx {:statement-id stmt-id})]
    (if exists
      (let [old-data (or (f/query-statement tx {:statement-id stmt-id
                                                :voided?      false})
                         (f/query-statement tx {:statement-id stmt-id
                                                :voided?      true}))
            old-stmt (-> old-data :payload u/parse-json)]
        (when-not (us/statement-equal? old-stmt new-stmt)
          (throw
           (ex-info "Statement Conflict!"
                    {:type :com.yetanalytics.lrs.protocol/statement-conflict
                     :extant-statement old-stmt
                     :statement        new-stmt}))))
      (do
        (f/insert-statement! tx (prepare-input input))
        (when (:voiding? input) (f/void-statement! tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor-input!
  [tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi])
                               (f/query-actor tx)
                               :payload
                               u/parse-json)]
    (let [new-actor (:payload input)
          {old-name "name"
           old-type "objectType" :or {old-type "Agent"}} old-actor
          {new-name "name"
           new-type "objectType" :or {new-type "Agent"}} new-actor]
      (cond
        ;; An Identified Group SHOULD NOT use Inverse Functional Identifiers
        ;; that are also used as Agent identifiers.
        (and (= "Agent" old-type) (= "Group" new-type))
        (do
          (log/warnf (str "An Agent is overriding a Group with the same IFI!\n"
                          "Agent: %s\n"
                          "Group: %s\n")
                     old-actor
                     new-actor)
          (f/update-actor! tx (prepare-input input)))
        (and (= "Group" old-type) (= "Agent" new-type))
        (do
          (log/warnf (str "A Group is overriding an Agent with the same IFI!\n"
                          "Group: %s\n"
                          "Agent: %s\n")
                     old-actor
                     new-actor)
          (f/update-actor! tx (prepare-input input)))
        ;; Update name
        (not= old-name new-name)
        (f/update-actor! tx (prepare-input input))
        ;; Identical actors -> no-op
        :else
        nil))
    (f/insert-actor! tx (prepare-input input)))
  nil)

(defn- insert-activity-input!
  [tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (f/query-activity tx)
                               :payload
                               u/parse-json)]
    (let [new-activ (:payload input)
          activity' (ua/merge-activities old-activ new-activ)
          input'    (assoc input :payload activity')]
      (f/update-activity! tx (prepare-input input')))
    (f/insert-activity! tx (prepare-input input)))
  nil)

(defn- insert-attachment-input!
  [tx input]
  (f/insert-attachment! tx input)
  nil)

(defn- insert-stmt-actor-input!
  [tx input]
  (f/insert-statement-to-actor! tx input)
  nil)

(defn- insert-stmt-activity-input!
  [tx input]
  (f/insert-statement-to-activity! tx input)
  nil)

(defn- insert-stmt-stmt-input!
  [tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (f/query-statement-exists tx input')]
    (when exists (f/insert-statement-to-statement! tx input)))
  nil)

(defn insert-statement!
  [tx {:keys [statement-input
              actor-inputs
              activity-inputs
              attachment-inputs
              stmt-actor-inputs
              stmt-activity-inputs
              stmt-stmt-inputs]}]
  (let [?stmt-id (insert-statement-input! tx statement-input)]
    (doall (map (partial insert-actor-input! tx) actor-inputs))
    (doall (map (partial insert-activity-input! tx) activity-inputs))
    (doall (map (partial insert-stmt-actor-input! tx) stmt-actor-inputs))
    (doall (map (partial insert-stmt-activity-input! tx) stmt-activity-inputs))
    (doall (map (partial insert-stmt-stmt-input! tx) stmt-stmt-inputs))
    (doall (map (partial insert-attachment-input! tx) attachment-inputs))
    ;; Return the statement ID (or nil on failure)
    ?stmt-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- query-res->statement
  [format ltags query-res]
  (-> query-res
      :payload
      u/parse-json
      (us/format-statement format ltags)))

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
  (let [{:keys [format attachments?]} input
        query-result (f/query-statement tx input)
        statement   (when query-result
                           (query-res->statement format ltags query-result))
        attachments (when (and statement attachments?)
                      (->> {:statement-id (get statement "id")}
                           (f/query-attachments tx)
                           (mapv conform-attachment-res)))]
    (cond-> {}
      statement   (assoc :statement statement)
      attachments (assoc :attachments attachments))))

(defn- query-many-statements
  "Query potentially multiple statements from the DB."
  [tx input ltags]
  (let [{:keys [format limit attachments? query-params]} input
        input'        (if limit (update input :limit inc) input)
        query-results (f/query-statements tx input')
        ?next-cursor  (when (and limit
                                 (= (inc limit) (count query-results)))
                        (-> query-results last :id u/uuid->str))
        stmt-results  (map (partial query-res->statement format ltags)
                           (if (not-empty ?next-cursor)
                             (butlast query-results)
                             query-results))
        att-results   (if attachments?
                        (->> (doall (map (fn [stmt]
                                           (->> (get stmt "id")
                                                (assoc {} :statement-id)
                                                (f/query-attachments tx)))
                                         stmt-results))
                             (apply concat)
                             (mapv conform-attachment-res))
                        [])]
    {:statement-result
     {:statements (vec stmt-results)
      :more       (if ?next-cursor
                    (us/make-more-url query-params ?next-cursor)
                    "")}
     :attachments att-results}))

(defn query-statements
  "Query statements from the DB. Return a map containing a singleton
   `:statement` if a statement ID is included in the query, or a
   `:statement-result` object otherwise. The map also contains `:attachments`
   to return any associated attachments. The `ltags` argument controls which
   language tag-value pairs are returned when `:format` is `:canonical`.
   Note that the `:more` property of `:statement-result` returned is a
   statement PK, NOT the full URL."
  [tx input ltags]
  (let [{?stmt-id :statement-id} input]
    (if ?stmt-id
      (query-one-statement tx input ltags)
      (query-many-statements tx input ltags))))

(defn query-descendants
  "Query Statement References from the DB. In addition to the immediate
   references given by `:statement-ref-id`, it returns ancestral
   references, i.e. not only the Statement referenced by `:statement-ref-id`,
   but the Statement referenced by that ID, and so on. The return value
   is a seq of the descendant statemnet IDs; these are later added to the
   input map."
  [tx input]
  (when-some [?sref-id (-> input :statement-input :statement-ref-id)]
    (->> {:ancestor-id ?sref-id}
         (f/query-statement-descendants tx)
         (map :descendant_id)
         (concat [?sref-id])
         vec)))
