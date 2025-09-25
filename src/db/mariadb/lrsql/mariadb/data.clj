(ns lrsql.mariadb.data
  (:require [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u])
  (:import [clojure.lang IPersistentMap]
           [java.sql PreparedStatement ResultSetMetaData Timestamp]
           [java.security MessageDigest]
           [java.time Instant]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;JSON read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn mariadb-read-json [s]
      (u/parse-json s :keyword-keys? true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;JWT hashing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sha256-base64
  [^String input]
  (let [bytes  (.getBytes input "UTF-8")
        digest (.digest (MessageDigest/getInstance "SHA-256") bytes)]
    (.encodeToString (java.util.Base64/getEncoder) digest)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reading 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-read! [{:keys [json-columns keyword-columns]}]
  (extend-protocol ReadableColumn
    String

    (read-column-by-label [^String s ^String label]
      (if (json-columns label)
        (u/parse-json s :keyword-keys? (some? (keyword-columns label)))
        s))

    (read-column-by-index [^String s ^ResultSetMetaData rsmeta ^long i]
      (let [label (.getColumnLabel rsmeta i)
            col-type-name (.getColumnTypeName rsmeta i)]

        (cond (and (= col-type-name "CHAR")
                   (= (count s) 36))
              (java.util.UUID/fromString s)

              (or (json-columns label)
                  (= col-type-name "JSON"))
              (u/parse-json s :keyword-keys? (some? (keyword-columns label)))

              :else s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-edn->json! []
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement stmt ^long i]
      (.setObject stmt i (u/write-json-str m)))))

(defn set-write-uuid->str! []
  (extend-protocol SettableParameter
    UUID
    (set-parameter [^UUID u ^PreparedStatement stmt ^long i]
      (.setString stmt i (u/uuid->str u)))))

(defn set-write-inst->timestamp! []
  (extend-protocol SettableParameter
    Instant
    (set-parameter [^Instant inst ^PreparedStatement stmt ^long i]
      (.setTimestamp stmt i (Timestamp/from inst)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Field Coercion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def type->mdb-type
  {:bool   "BOOLEAN"
   :int    "INTEGER"
   :dec    "DECIMAL"
   :string "CHAR"
   :json   "JSON"})
