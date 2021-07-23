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
           [java.sql Blob PreparedStatement ResultSetMetaData]))

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
  [json-labels label ^"[B" b]
  (let [label (cstr/lower-case label)]
    (if (json-labels label) (u/parse-json b) b)))

(defn set-read-bytes->json!
  "Set reading byte arrays as JSON data if the column label is a member of the
   set `json-labels`."
  [json-labels]
  ;; Note: due to a long-standing bug, the byte array extension needs to come
  ;; first: https://clojure.atlassian.net/browse/CLJ-1381#icft=CLJ-1381
  (extend-protocol ReadableColumn
    (Class/forName "[B")
    (read-column-by-label [^"[B" b ^String label]
      (parse-json-payload json-labels label b))
    (read-column-by-index [^"[B" b ^ResultSetMetaData rsmeta ^long i]
      (parse-json-payload json-labels (.getColumnLabel rsmeta i) b))))

(defn set-read-blob->json!
  "Set reading java.sql.Blob instances as JSON data if the column label is a
   member of the set `json-labels`."
  [json-labels]
  (extend-protocol ReadableColumn
    Blob ; SQL Blobs - convert to bytes
    (read-column-by-label [^Blob b ^String label]
      (parse-json-payload json-labels
                          label
                          (.getBytes b 1 (.length b))))
    (read-column-by-index [^Blob b ^ResultSetMetaData rsmeta ^long i]
      (parse-json-payload json-labels
                          (.getColumnLabel rsmeta i)
                          (.getBytes b 1 (.length b))))))

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
