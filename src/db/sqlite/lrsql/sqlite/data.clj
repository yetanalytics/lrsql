(ns lrsql.sqlite.data
  "Similar to `lrsql.backend.data` except that this is SQLite-specific."
  (:require [clojure.string :as cstr]
            [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import [java.util UUID]
           [java.time Instant]
           [java.sql PreparedStatement ResultSetMetaData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-sqlite-string
  [uuid-labels ts-labels label s]
  (let [label (cstr/lower-case label)]
    (cond
      ;; UUIDs
      (uuid-labels label)
      (u/str->uuid s)
      ;; Timestamps
      ;; TODO: the only place where timestamps are queried is in document
      ;; queries, where they are immediately re-converted to strings.
      ;; Should this be skipped then?
      (ts-labels label)
      (u/str->time s)
      :else
      s)))

(defn- parse-sqlite-int
  [bool-labels label n]
  (let [label (cstr/lower-case label)]
    (if (bool-labels label)
      ;; Booleans
      (not (zero? n))
      ;; Integers
      n)))

(defn set-read-str->uuid-or-inst!
  "Set reading strings as UUIDs if the column label in `uuid-labels`, or as
   Instants if the label is in `ts-labels`."
  [uuid-labels ts-labels]
  (extend-protocol ReadableColumn
    String
    (read-column-by-label [^String s ^String label]
      (parse-sqlite-string uuid-labels ts-labels label s))
    (read-column-by-index [^String s ^ResultSetMetaData rsmeta ^long i]
      (parse-sqlite-string uuid-labels
                           ts-labels
                           (.getColumnLabel rsmeta i)
                           s))))

(defn set-read-int->bool!
  "Set reading ints as booleans if the column label is a member of the
   set `bool-labels`."
  [bool-labels]
  (extend-protocol ReadableColumn
    Integer
    (read-column-by-label [^Integer n ^String label]
      (parse-sqlite-int bool-labels label n))
    (read-column-by-index [^Integer n ^ResultSetMetaData rsmeta ^long i]
      (parse-sqlite-int bool-labels (.getColumnLabel rsmeta i) n))))

(defn parse-query-reaction-result
  "Reaction condition statements are not automatically coerced because the
  columns are aliased. Properly parse reaction results."
  [result-rows]
  (mapv
   (fn [row]
     (into {}
           (for [[condition-name statement-bs] row]
             [condition-name
              (with-open [r (io/reader statement-bs)]
                (json/parse-stream r))])))
   result-rows))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-uuid->str!
  "Set writing UUIDs as strings."
  []
  (extend-protocol SettableParameter
    UUID
    (set-parameter [^UUID u ^PreparedStatement s ^long i]
      (.setString s i (u/uuid->str u)))))

(defn set-write-inst->str!
  "Set normalizing and writing Instant timestamps as strings."
  []
  (extend-protocol SettableParameter
    Instant
    (set-parameter [^Instant ts ^PreparedStatement s ^long i]
      (.setString s i (u/time->str ts)))))

(defn set-write-bool->int!
  "Set writing boolean values as ints (`0` is `false`, `1` is `true`)."
  []
  (extend-protocol SettableParameter
    Boolean
    (set-parameter [^Boolean b ^PreparedStatement s ^long i]
      (.setInt s i (if b 1 0)))))
