(ns lrsql.backend.data
  "Namespace for type conversions between SQL and Clojure datatypes
   during DB interaction. All public functions extend either
   the SettableParameter or ResultColumn protocols from next.jdbc
   for reading or writing data, respectively."
  (:require [clojure.string :as cstr]
            [next.jdbc.date-time :refer [read-as-instant]]
            [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u])
  (:import [clojure.lang IPersistentMap]
           [java.sql PreparedStatement ResultSetMetaData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Instant

(defn set-read-time->instant!
  "Set reading java.sql.Data and java.sql.Timestamp data as Instants."
  []
  (read-as-instant))

;; JSON

(defn- parse-json-payload
  [json-labels json-kw-labels label ^"[B" b]
  (let [label (cstr/lower-case label)]
    (cond
      (json-labels label) (u/parse-json b)
      (json-kw-labels label) (u/parse-json b :keyword-keys? true)
      :else b)))

(defn set-read-bytes->json!
  "Set reading byte arrays as JSON data if the column label is a member of the
   set `json-labels`."
  [json-labels json-kw-labels]
  ;; Note: due to a long-standing bug, the byte array extension needs to come
  ;; first: https://clojure.atlassian.net/browse/CLJ-1381#icft=CLJ-1381
  ;;
  ;; Note: clj-kondo does not recognize the use of a non-simple-symbol in
  ;; `extend-protocol` so we add the ignore (make sure to comment it out if
  ;; you're editing this code).
  #_{:clj-kondo/ignore [:syntax]}
  (extend-protocol ReadableColumn
    (Class/forName "[B") ; Evaulates to `[B`
    (read-column-by-label [^"[B" b ^String label]
      (parse-json-payload json-labels json-kw-labels label b))
    (read-column-by-index [^"[B" b ^ResultSetMetaData rsmeta ^long i]
      (parse-json-payload json-labels json-kw-labels (.getColumnLabel rsmeta i) b))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-json->bytes!
  "Set writing JSON data (i.e. any IPersistentMap instance) as a byte array."
  []
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement s ^long i]
      (.setBytes s i (u/write-json m)))))
