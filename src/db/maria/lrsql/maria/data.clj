(ns lrsql.maria.data
  (:require [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u]
            [clojure.data.json :as json])
  (:import [clojure.lang IPersistentMap]
           [java.sql PreparedStatement ResultSetMetaData]
           [java.security MessageDigest]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;JSON read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn maria-read-json [s]
      (u/parse-json s :keyword-keys? true))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;JWT hashing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sha256-base64
  [^String input]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes input "UTF-8"))]
    (.encodeToString (java.util.Base64/getEncoder) digest)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; "How Do You Read a DB Like Maria?"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def holder (atom nil))

(defn set-read-experiment! [{:keys [json-columns keyword-columns]}]
  (extend-protocol ReadableColumn
    String
    (read-column-by-label [^String s ^String label]
      #_(println "read-column-by-label: " s " " label)
      (if (json-columns label)
        (let [res (u/parse-json s :keyword-keys? (some? (keyword-columns label)))]
          #_(println "before parse:" s)
          #_(println "after parse:" res)
          res)
        s))
    
    (read-column-by-index [^String s ^ResultSetMetaData rsmeta ^long i]

      (reset! holder rsmeta)
      (let [label (.getColumnLabel rsmeta i)
            col-type (.getColumnType rsmeta i)
            col-type-name (.getColumnTypeName rsmeta i)
            table-name (.getTableName rsmeta i)
            #_#__ (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
            #_#__ (println "col label" label)
            #_#__ (println "col-type:" col-type)
            #_#__ (println "col-type-name:" col-type-name)
            #_#__ (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
            ]

        (cond (and (= col-type-name "CHAR")
                   (= (count s) 36))
              (java.util.UUID/fromString s)

              (or (json-columns label)
                  (= col-type-name "JSON"))
              (do #_(println "json detected!")
                  #_(println "json:" s)
                  (let [res (u/parse-json s :keyword-keys? (some? (keyword-columns label)))]
                    #_(println "parsed:" res)
                    res))

              :else
              s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-edn->json! []
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement stmt ^long i]
      (.setObject stmt i (json/write-str m)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timezone Input
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def local-tz-input
  "Returns a properly formatted hug input map to inject a timezone id into a
  query needing a timezone id"
  {:tz-id (str "'" u/local-zone-id "'")})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Field Coercion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def type->mdb-type
  {:bool   "BOOLEAN"
   :int    "INTEGER"
   :dec    "DECIMAL"
   :string "CHAR"
   :json   "JSON"})
