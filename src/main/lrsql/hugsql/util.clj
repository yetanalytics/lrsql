(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid]))

(defn current-time
  "Return the current time as a java.util.Instant object."
  []
  (jt/instant))

(defn generate-uuid
  "Return a new sequential UUID."
  []
  (clj-uuid/squuid))

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (jt/instant ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp to a string."
  [ts]
  (jt/format ts))
