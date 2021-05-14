(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid :as uuid]))

(defn current-time
  "Return the current time as a java.util.Instant object."
  []
  (jt/instant))

(defn generate-uuid
  "Return a new sequential UUID."
  []
  (uuid/squuid))

(defn parse-uuid
  "Parse a string into an UUID."
  [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn parse-time
  "Parse a string into a java.util.Instant timestamp."
  [time-str]
  (jt/instant time-str))
