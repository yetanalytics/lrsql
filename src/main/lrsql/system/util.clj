(ns lrsql.system.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [ring.util.codec :refer [form-encode form-decode]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro assert-config
  [spec component-name config]
  `(when-some [err# (s/explain-data ~spec ~config)]
     (do
       (log/errorf "Configuration errors:\n%s"
                   (with-out-str (s/explain-out err#)))
       (throw (ex-info (format "Invalid %s configuration!"
                               ~component-name)
                       {:type ::invalid-config
                        :error-data err#})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JDBC Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-db-props
  "Given `prop-str` of the form \"key=value&key=value...\", return a
   keyword-key map of property names to values."
  [prop-str]
  (reduce-kv (fn [m k v] (assoc m (keyword k) (form-encode v)))
             {}
             (form-decode prop-str)))

(defn make-jdbc-url
  "Construct the JDBC URL from DB properties; if `:db-jdbc-url` is already
   present, return that."
  [{:keys [db-type
           db-name
           db-host
           db-port
           db-properties
           db-jdbc-url]}]
  (if db-jdbc-url
    ;; If JDBC URL is given directly, this overrides all
    db-jdbc-url
    ;; Construct a new JDBC URL from config vars
    (cond-> {:dbtype db-type
             :dbname db-name
             :host   db-host
             :port   db-port}
      db-properties
      (merge (parse-db-props db-properties))
      true
      jdbc-conn/jdbc-url)))
