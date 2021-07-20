(ns lrsql.ops.query.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
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
  [inf tx input ltags]
  (let [{:keys [format attachments?]} input
        query-result (ip/-query-statement inf tx input)
        statement   (when query-result
                      (query-res->statement format ltags query-result))
        attachments (when (and statement attachments?)
                      (->> {:statement-id (get statement "id")}
                           (ip/-query-attachments inf tx)
                           (mapv conform-attachment-res)))]
    (cond-> {}
      statement   (assoc :statement statement)
      attachments (assoc :attachments attachments))))

(defn- query-many-statements
  "Query potentially multiple statements from the DB."
  [inf tx input ltags]
  (let [{:keys [format limit attachments? query-params]} input
        input'        (if limit (update input :limit inc) input)
        query-results (ip/-query-statements inf tx input')
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
                                                (assoc {} :statement-id)
                                                (ip/-query-attachments inf tx)))
                                         stmt-results)
                                    (apply concat)
                                    (map conform-attachment-res)))
                        [])]
    {:statement-result
     {:statements (vec stmt-results)
      :more       (if ?next-cursor
                    (us/make-more-url query-params ?next-cursor)
                    "")}
     :attachments att-results}))

(s/fdef query-statements
  :args (s/cat :inf ss/statement-interface?
               :tx transaction?
               :input ss/statement-query-spec
               :ltags ss/lang-tags-spec)
  :ret ::lrsp/get-statements-ret)

(defn query-statements
  "Query statements from the DB. Return a map containing a singleton
   `:statement` if a statement ID is included in the query, or a
   `:statement-result` object otherwise. The map also contains `:attachments`
   to return any associated attachments. The `ltags` argument controls which
   language tag-value pairs are returned when `:format` is `:canonical`.
   Note that the `:more` property of `:statement-result` returned is a
   statement PK, NOT the full URL."
  [inf tx input ltags]
  (let [{?stmt-id :statement-id} input]
    (if ?stmt-id
      (query-one-statement inf tx input ltags)
      (query-many-statements inf tx input ltags))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Descendant Querying
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-descendants
  :args (s/cat :inf ss/statement-interface?
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
  [inf tx input]
  (if-some [?sref-id (-> input :statement-input :statement-ref-id)]
    (->> {:ancestor-id ?sref-id}
         (ip/-query-statement-descendants inf tx)
         (map :descendant_id)
         (concat [?sref-id])
         vec)
    []))
