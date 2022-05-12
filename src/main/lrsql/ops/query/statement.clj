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

(defn- dedupe-attachment-res
  [attachment-query-res]
  (loop [res-in  attachment-query-res
         res-out []
         seen    #{}]
    (if-let [{att-sha :attachment_sha :as att} (first res-in)]
      (if (contains? seen att-sha)
        (recur (rest res-in)
               res-out
               seen)
        (recur (rest res-in)
               (conj res-out att)
               (conj seen att-sha)))
      res-out)))

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
        statement   (when query-result
                      (query-res->statement format ltags query-result))
        attachments (when (and statement attachments?)
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
                        (doall (->> (mapcat
                                     (fn [stmt]
                                       (->> (get stmt "id")
                                            u/str->uuid
                                            (assoc {} :statement-id)
                                            (bp/-query-attachments bk tx)))
                                     stmt-results)
                                    dedupe-attachment-res
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
