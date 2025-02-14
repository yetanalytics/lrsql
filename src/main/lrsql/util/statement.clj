(ns lrsql.util.statement
  (:require [ring.util.codec :refer [form-encode]]
            [com.yetanalytics.pathetic :as pa]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.util :as u]
            [lrsql.util.path :as up]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Preparation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; If a Statement lacks a version, the version MUST be set to 1.0.0
;; TODO: Change for version 2.0.0
;; FIXME: Why is this not version 1.0.3?
(def xapi-version "1.0.0")

;; NOTE: SQL LRS overwrites any pre-existing authority object in a statement, as
;; suggested by the spec:
;; https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#requirements-14

(defn- dissoc-empty-lang-maps*
  [{:strs [display name description] :as m}]
  (cond-> m
    (empty? display)     (dissoc "display")
    (empty? name)        (dissoc "name")
    (empty? description) (dissoc "description")))

(defn- dissoc-empty-lang-maps
  [statement]
  (cond-> statement
    ;; Dissoc empty verb display
    true
    (update "verb" dissoc-empty-lang-maps*)
    ;; Dissoc empty object activity name + description
    (= "Activity" (get-in statement ["object" "objectType"]))
    (update-in ["object" "definition"]
            (fn [{:strs [choices scale source target steps] :as obj-def}]
              (cond-> (dissoc-empty-lang-maps* obj-def)
                choices (update "choices" #(mapv dissoc-empty-lang-maps* %))
                scale   (update "scale" #(mapv dissoc-empty-lang-maps* %))
                source  (update "source" #(mapv dissoc-empty-lang-maps* %))
                target  (update "target" #(mapv dissoc-empty-lang-maps* %))
                steps   (update "steps" #(mapv dissoc-empty-lang-maps* %)))))
    ;; Dissoc empty attachemnt name + description
    (contains? statement "attachments")
    (update "attachments" #(mapv dissoc-empty-lang-maps* %))))

(defn- assoc-to-statement
  "Assoc while also changing the meta of `statement`."
  [statement k v]
  (-> statement
      (assoc k v)
      (vary-meta update
                 :assigned-vals
                 conj
                 (keyword k))))

(defn prepare-statement
  "Prepare `statement` for LRS storage by coll-ifying context activities
   and setting missing id, timestamp, authority, version, and stored
   properties. In addition, removes empty maps from `statement`."
  [authority statement]
  (let [{?id        "id"
         ?timestamp "timestamp"
         ?version   "version"}
        statement
        {squuid      :squuid
         squuid-ts   :timestamp
         squuid-base :base-uuid}
        (u/generate-squuid*)
        squuid-ts-str
        (u/time->str squuid-ts)
        {{activity-def* "definition"} "object"
         context* "context"
         result*  "result"
         :as statement*}
        (-> statement
            dissoc-empty-lang-maps
            ss/fix-statement-context-activities
            (vary-meta assoc :assigned-vals #{})
            (vary-meta assoc :primary-key squuid)
            (assoc-to-statement "stored" squuid-ts-str)
            (assoc-to-statement "authority" authority))]
    (cond-> statement*
      ;; Dissoc empty properties
      (empty? activity-def*)
      (update "object" dissoc "definition")
      (empty? context*)
      (dissoc "context")
      (empty? result*)
      (dissoc "result")
      ;; Assoc missing properties
      (not ?id)
      (assoc-to-statement "id" (u/uuid->str squuid-base))
      (not ?timestamp)
      (assoc-to-statement "timestamp" squuid-ts-str)
      (not ?version)
      (assoc-to-statement "version" xapi-version))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Equality
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn statement-equal?
  "Compare two Statements based on their immutable properties."
  [stmt1 stmt2]
  (ss/statements-immut-equal? stmt1 stmt2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn format-statement
  "Given `statement`, format it according to the value of `format`:
   - :exact      No change to the Statement
   - :ids        Return only the IDs in each Statement object
   - :canonical  Return a \"canonical\" version of lang maps based on `ltags`."
  [statement format ltags]
  (case format
    :exact
    statement
    :ids
    (ss/format-statement-ids statement)
    :canonical
    (ss/format-canonical statement ltags)
    ;; else
    (throw (ex-info "Unknown format type"
                    {:type   ::unknown-format-type
                     :format format}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Pre-query

(defn ensure-default-max-limit
  "Given `?limit`, apply the maximum possible limit (if it is zero
   or exceeds that limit) or the default limit (if it is `nil`).
   The maximum and default limits are set in as environment vars."
  [{?limit :limit
    :as    params}
   {limit-max     :stmt-get-max
    limit-default :stmt-get-default
    :as _lrs-config}]
  (assoc params
         :limit
         (cond
           ;; Ensure limit is =< max
           (pos-int? ?limit)
           (min ?limit limit-max)
           ;; If zero, spec says use max
           (and ?limit (zero? ?limit))
           limit-max
           ;; Otherwise, apply default
           :else
           limit-default)))

;; Post-query

(defn make-more-url
  "Forms the `more` URL value from `query-params`, the URL prefix `prefix` and
   the Statement PK `next-cursor` which points to the first Statement of the
   next page."
  [query-params prefix next-cursor]
  (let [{?agent :agent} query-params]
    (str prefix
         "/statements?"
         (form-encode
          (cond-> query-params
            true   (assoc :from (u/uuid->str next-cursor))
            ?agent (assoc :agent (u/write-json-str ?agent)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement CSV
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private json-path-opts
  {:return-missing?    true
   :return-duplicates? false})

(defn property-paths->json-paths
  [property-paths]
  (mapv up/path->jsonpath-vec property-paths))

(defn property-paths->csv-headers
  [property-paths]
  (mapv up/path->csv-header property-paths))

(defn statement->csv-row
  [json-paths statement]
  (pa/get-values* statement json-paths json-path-opts))

(defn statements->csv-seq
  "Converts a lazy `statement-seq` into a lazy seq of CSV data in the
   form of vectors of vectors representing row data. The first vector
   is the headers, parsed from `property-paths`."
  [property-paths statements-seq]
  (let [json-paths  (mapv up/path->jsonpath-vec property-paths)
        csv-headers (mapv up/path->csv-header property-paths)
        stmt->row   (partial statement->csv-row json-paths)]
    (->> statements-seq
         (map stmt->row)
         (cons csv-headers)
         lazy-seq)))
