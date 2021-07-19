(ns lrsql.data
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
;; Common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-json-payload
  [^"[B" b label]
  (let [label (cstr/lower-case label)]
    (if (#{"payload"} label) (u/parse-json b) b)))

(defn- set-json-write!
  "Extend the SettableParameter protocol to write Clojure maps (i.e. JSON/EDN)
   as bytes."
  []
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement s ^long i]
      (.setBytes s i (u/write-json m)))))

(defn- set-json-read!
  "Extend the ReadableColumn protocol to read `payload` bytes as Clojure maps
   (i.e. JSON/EDN). All instances of java.sql.Blob are converted to byte
   arrays if they are not JSON."
  []
  (extend-protocol ReadableColumn
    ;; Note: due to a long-standing bug, the byte array extension needs to come
    ;; first: https://clojure.atlassian.net/browse/CLJ-1381#icft=CLJ-1381

    (Class/forName "[B") ; Byte arrays
    (read-column-by-label [^"[B" b ^String label]
      (parse-json-payload b label))
    (read-column-by-index [^"[B" b ^ResultSetMetaData rsmeta ^long i]
      (parse-json-payload b (.getColumnLabel rsmeta i)))
    
    Blob ; SQL Blobs - convert to bytes
    (read-column-by-label [^Blob b ^String label]
      (parse-json-payload (.getBytes b 1 (.length b)) label))
    (read-column-by-index [^Blob b ^ResultSetMetaData rsmeta ^long i]
      (parse-json-payload (.getBytes b 1 (.length b))
                          (.getColumnLabel rsmeta i)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; H2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-h2-write!
  "Set write behavior for H2 DBs:
   - Write JSON to byte arrays"
  []
  ;; JSON
  (set-json-write!))

(defn set-h2-read!
  "Set read behavior for H2 DBs:
   - Read JSON from byte arrays (if the column label is `payload`)
   - Read java.time.Instants from java.sql.Dates"
  []
  ;; Timestamps
  (read-as-instant)
  ;; JSON
  (set-json-read!))
