(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid]
            [clojure.data.json :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro wrap-parse-fn
  "Wrap `(parse-fn s)` in an exception such that on parse failure, the
   folloiwing error data is thrown:
     :kind      ::parse-failure
     :data      `data`
     :str-type  `str-type`"
  [parse-fn data-type data]
  `(try (~parse-fn ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse nil or invalid %s"
                                  ~data-type)
                          {:kind     ::parse-failure
                           :data     ~data
                           :daa-type ~data-type})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-uuid
  "Return a new sequential UUID."
  []
  (clj-uuid/squuid))

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (wrap-parse-fn java.util.UUID/fromString "UUID" uuid-str))

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (jt/instant))

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (wrap-parse-fn jt/instant "timestamp" ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp into a string."
  [ts]
  (jt/format ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-json*
  [data]
  (cond
    (string? data)
    (json/read-str data)
    (bytes? data) ; H2 returns JSON data as a byte array
    (json/read-str (String. data))))

(defn parse-json
  "Parse `data` into JSON format. `data` may be a string or a byte array."
  [data]
  (wrap-parse-fn parse-json* "JSON" data))

(defn write-json
  "Write `jsn` to a string."
  [jsn]
  (json/write-str jsn))
