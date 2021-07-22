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
           [java.util UUID]
           [java.time Instant]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SQLite
;; SQLite does not support UUIDs, timestamps, or even booleans natively
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-sqlite-write!
  "Set write behavior for SQLite DBs:
   - Writing JSON as BLOBs
   - Writing UUIDs and Instants as TEXTs
   - Writing Booleans as INTEGERs"
  []
  ;; JSON
  (set-json-write!)
  ;; SQLite-specific
  (extend-protocol SettableParameter
    UUID
    (set-parameter [^UUID u ^PreparedStatement s ^long i]
      (.setString s i (u/uuid->str u)))
    Instant
    (set-parameter [^Instant ts ^PreparedStatement s ^long i]
      (.setString s i (u/time->str ts)))
    Boolean
    (set-parameter [^Boolean b ^PreparedStatement s ^long i]
      (.setInt s i (if b 1 0)))))

(defn- parse-sqlite-string
  [s label]
  (let [label (cstr/lower-case label)]
    (cond
      ;; UUIDs
      (or (#{"registration"} label)
          (and (re-matches #".*id" label)
               (not (#{"state_id" "profile_id"} label))))
      (u/str->uuid s)
      ;; Timestamps
      ;; TODO: the only place where timestamps are queried is in document
      ;; queries, where they are immediately re-converted to strings.
      ;; Should this be skipped then?
      (#{"last_modified"} label)
      (u/str->time s)
      :else
      s)))

(defn- parse-sqlite-int
  [n label]
  (let [label (cstr/lower-case label)]
    (if (#{"is_voided"} label)
      ;; Booleans
      (not (zero? n))
      ;; Integers
      n)))

(defn set-sqlite-read!
  "Set read behavior for SQLite DBs:
   - Reading JSON from BLOBs
   - Reading UUIDs and Instants from TEXTs
   - Reading Booleans from INTEGERs
   All of the above behavior depends on the label of the column being read."
  []
  ;; JSON
  (set-json-read!)
  ;; SQLite-specific
  (extend-protocol ReadableColumn
    String
    (read-column-by-label [^String s ^String label]
      (parse-sqlite-string s label))
    (read-column-by-index [^String s ^ResultSetMetaData rsmeta ^long i]
      (parse-sqlite-string s (.getColumnLabel rsmeta i)))
    Integer
    (read-column-by-label [^Integer n ^String label]
      (parse-sqlite-int n label))
    (read-column-by-index [^Integer n ^ResultSetMetaData rsmeta ^long i]
      (parse-sqlite-int n (.getColumnLabel rsmeta i)))))
