(ns lrsql.postgres.data
  (:require [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u])
  (:import [clojure.lang IPersistentMap]
           [org.postgresql.util PGobject]
           [java.sql PreparedStatement ResultSetMetaData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PGObject
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- json->pg-object
  [type jsn]
  (doto (PGobject.)
    (.setType type)
    (.setValue (u/write-json-str jsn))))

(defn- pg-object->json
  [kw-labels label ^PGobject pg-obj]
  (let [type  (.getType pg-obj)
        value (.getValue pg-obj)]
    (if (#{"jsonb" "json"} type)
      (u/parse-json value :keyword-keys? (some? (kw-labels label)))
      (throw (ex-info "Invalid PostgreSQL JSON type"
                      {:type       ::invalid-postgres-json
                       :json-type  type
                       :json-value value})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-read-pgobject->json!
  [kw-labels]
  (extend-protocol ReadableColumn
    PGobject
    (read-column-by-label [^PGobject pg-obj ^String label]
      (pg-object->json kw-labels label pg-obj))
    (read-column-by-index [^PGobject pg-obj ^ResultSetMetaData rsmeta ^long i]
      (pg-object->json kw-labels (.getColumnLabel rsmeta i) pg-obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-json->pgobject!
  [type]
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement stmt ^long i]
      (.setObject stmt i (json->pg-object type m)))))

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

(def type->pg-type
  {:bool "BOOLEAN"
   :int "INTEGER"
   :dec "DECIMAL"
   :string "TEXT"})
