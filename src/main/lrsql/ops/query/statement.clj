(ns lrsql.ops.query.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.data.csv :as csv]
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

(defn- query-statement-attachments
  "Query all attachments associated with the ID of the `statement`."
  [bk tx statement]
  (->> (get statement "id")
       u/str->uuid
       (assoc {} :statement-id)
       (bp/-query-attachments bk tx)))

(defn- query-one-statement
  "Query a single statement from the DB, using the `:statement-id` parameter."
  [bk tx input ltags]
  (let [{:keys [format attachments?]} input
        query-result (bp/-query-statement bk tx input)
        statement    (when query-result
                       (query-res->statement format ltags query-result))
        attachments  (when (and statement attachments?)
                       (->> statement
                            (query-statement-attachments bk tx)
                            (mapv conform-attachment-res)))]
    (cond-> {}
      statement   (assoc :statement statement)
      attachments (assoc :attachments attachments))))

(defn- query-many-statements*
  "Query multiple statements and return the (nilable) cursor to the next
   statement."
  [bk tx input ltags]
  (let [{:keys [format limit]} input
        input*         (cond-> input
                         (some? limit)
                         (update :limit inc))
        query-results  (bp/-query-statements bk tx input*)
        ?next-cursor   (when (and limit
                                  (= (inc limit) (count query-results)))
                         (-> query-results last :id))
        query-results* (if (some? ?next-cursor)
                         (butlast query-results)
                         query-results)
        stmt-results   (map (partial query-res->statement format ltags)
                            query-results*)]
    {:statement-results stmt-results
     :?next-cursor      ?next-cursor}))

(defn- query-many-statements
  "Query potentially multiple statements from the DB."
  [bk tx input ltags prefix]
  (let [{:keys [attachments? query-params]}
        input
        {:keys [statement-results ?next-cursor]}
        (query-many-statements* bk tx input ltags)
        attachment-results
        (if attachments?
          (vec (doall (->> (mapcat
                            (partial query-statement-attachments bk tx)
                            statement-results)
                           dedupe-attachment-res
                           (map conform-attachment-res))))
          [])]
    {:statement-result
     {:statements (vec statement-results)
      :more       (if ?next-cursor
                    (us/make-more-url query-params prefix ?next-cursor)
                    "")}
     :attachments attachment-results}))

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

(s/fdef query-statements-stream
  :args (s/cat :bk ss/statement-backend?
               :tx transaction?
               :input ss/statement-query-many-spec
               :ltags ss/lang-tags-spec
               :property-paths vector?
               :writer #(instance? java.io.Writer %)))

(defn query-statements-stream
  "Stream all the statements in the database, filtered by `input`, to `writer`
   as CSV data. The `:limit` parameter will be ignored. Attachments are not
   included."
  [bk tx input ltags property-paths writer]
  (let [format      (:format input)
        input       (dissoc input :from :limit :query-params)
        json-paths  (us/property-paths->json-paths property-paths)
        csv-headers (us/property-paths->csv-headers property-paths)]
    (csv/write-csv writer [csv-headers] :newline :cr+lf)
    (transduce (comp (map (fn [res]
                            (query-res->statement format ltags res)))
                     (map (fn [stmt]
                            (us/statement->csv-row json-paths stmt))))
               (fn write-csv-reducer
                 ([writer]
                  writer)
                 ([writer row]
                  (csv/write-csv writer [row] :newline :cr+lf)
                  writer))
               writer
               (bp/-query-statements-lazy bk tx input))
    writer))

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
