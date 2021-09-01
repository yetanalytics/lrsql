(ns lrsql.ops.query.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.statement :as ss]
            [lrsql.util :as u]
            [lrsql.util.statement :as us]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Querying
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- query-res->statement
  [format ltags query-res]
  (-> query-res :payload (us/format-statement format ltags)))

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
  [bk tx input ltags]
  (let [{:keys [format attachments?]} input
        query-result (bp/-query-statement bk tx input)
        statement    (when query-result
                       (query-res->statement format ltags query-result))
        attachments  (when (and statement attachments?)
                       (->> (get statement "id")
                            u/str->uuid
                            (assoc {} :statement-id)
                            (bp/-query-attachments bk tx)
                            (mapv conform-attachment-res)))]
    (cond-> {}
      statement   (assoc :statement statement)
      attachments (assoc :attachments attachments))))

(defn- query-many-statements
  "Query potentially multiple statements from the DB."
  [bk tx input ltags prefix]
  (let [{:keys [format limit attachments? query-params]} input
        input'        (if limit (update input :limit inc) input)
        query-results (bp/-query-statements bk tx input')
        ?next-cursor  (when (and limit
                                 (= (inc limit) (count query-results)))
                        (-> query-results last :id u/uuid->str))
        stmt-results  (map (partial query-res->statement format ltags)
                           (if (not-empty ?next-cursor)
                             (butlast query-results)
                             query-results))
        att-results   (if attachments?
                        (doall (->> (map (fn [stmt]
                                           (->> (get stmt "id")
                                                u/str->uuid
                                                (assoc {} :statement-id)
                                                (bp/-query-attachments bk tx)))
                                         stmt-results)
                                    (apply concat)
                                    (map conform-attachment-res)))
                        [])]
    {:statement-result
     {:statements (vec stmt-results)
      :more       (if ?next-cursor
                    (us/make-more-url query-params prefix ?next-cursor)
                    "")}
     :attachments att-results}))

(s/fdef query-statements
  :args (s/cat :bk ss/statement-backend?
               :tx transaction?
               :input ss/statement-query-spec
               :ltags ss/lang-tags-spec
               :prefix string?)
  :ret ::lrsp/get-statements-ret)

(defn query-statements
  "Query statements from the DB. Return a map containing a singleton
   `:statement` if a statement ID is included in the query, or a
   `:statement-result` object otherwise. The map also contains `:attachments`
   to return any associated attachments. The `ltags` argument controls which
   language tag-value pairs are returned when `:format` is `:canonical`.
   Note that the `:more` property of `:statement-result` returned is a
   statement PK, NOT the full URL."
  [bk tx input ltags prefix]
  (let [{?stmt-id :statement-id} input]
    (if ?stmt-id
      (query-one-statement bk tx input ltags)
      (query-many-statements bk tx input ltags prefix))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Existence Checking
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::extant-statement ::ss/payload)
(s/def ::statement ::ss/payload)

(s/fdef query-statement-conflict
  :args (s/cat :bk ss/statement-backend?
               :tx transaction?
               :input ss/insert-statement-input-spec)
  :ret (s/or :ok nil?
             :error (s/keys :req-un [::extant-statement ::statement])))

(defn query-statement-conflict
  "Check if a Statement already exists in the DB. If so, then return a map
   containing `:extant-statement` (the pre-existing Statement) and
   `:statement` (the one being inserted).
   
   We perform this query separately from Statement insertion in order to
   allow for separate transactions for each Statement query and insertion,
   which will help us avoid deadlocks."
  [bk tx input]
  (let [{{stmt-id :statement-id stmt-old :payload} :statement-input} input]
    (when-some [stmt-new (->> {:statement-id stmt-id}
                            (bp/-query-statement bk tx)
                            :payload)]
      {:extant-statement stmt-old
       :statement        stmt-new
       :equal?           (us/statement-equal? stmt-old stmt-new)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Descendant Querying
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-descendants
  :args (s/cat :bk ss/statement-backend?
               :tx transaction?
               :input ss/insert-statement-input-spec)
  :ret (s/coll-of ::ss/descendant-id :kind vector? :gen-max 5))

(defn query-descendants
  "Query Statement References from the DB. In addition to the immediate
   references given by `:statement-ref-id`, it returns ancestral
   references, i.e. not only the Statement referenced by `:statement-ref-id`,
   but the Statement referenced by that ID, and so on. The return value
   is a vec of the descendant statement IDs; these are later added to the
   input map."
  [bk tx input]
  (if-some [?sref-id (-> input :statement-input :statement-ref-id)]
    (->> {:ancestor-id ?sref-id}
         (bp/-query-statement-descendants bk tx)
         (map :descendant_id)
         (concat [?sref-id])
         vec)
    []))
