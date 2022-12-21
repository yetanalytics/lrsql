(ns lrsql.postgres.data
  (:require [next.jdbc.prepare :refer [SettableParameter]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [lrsql.util :as u])
  (:import [clojure.lang IPersistentMap]
           [org.postgresql.util PGobject]
           [java.sql PreparedStatement]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PGObject
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- json->pg-object
  [jsn]
  (doto (PGobject.)
    (.setType "json")
    (.setValue (u/write-json-str jsn))))

(defn- pg-object->json
  [^PGobject pg-obj]
  (let [type  (.getType pg-obj)
        value (.getValue pg-obj)]
    (if (#{"json"} type)
      (u/parse-json value)
      (throw (ex-info "Invalid PostgreSQL JSON type"
                      {:type       ::invalid-postgres-json
                       :json-type  type
                       :json-value value})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-read-pgobject->json!
  []
  (extend-protocol ReadableColumn
    PGobject
    (read-column-by-label [^PGobject pg-obj _]
      (pg-object->json pg-obj))
    (read-column-by-index [^PGobject pg-obj _2 _3]
      (pg-object->json pg-obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Write
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-write-json->pgobject!
  []
  (extend-protocol SettableParameter
    IPersistentMap
    (set-parameter [^IPersistentMap m ^PreparedStatement stmt ^long i]
      (.setObject stmt i (json->pg-object m)))))
