(ns lrsql.system.util
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [ring.util.codec :refer [form-encode form-decode]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers and Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def redactable-config-vars
  #{:db-password
    :admin-pass-default
    :api-secret-default
    :key-password})

(defn redact-config-vars
  "Given a `config` map, replace redactable values with \"[REDACTED]\"
   (if it is a string; keywords and symbols are treated similarly).
   See `redactable-config-vars` for which vars are redactable."
  [config]
  (w/postwalk (fn [node]
                (if (map-entry? node)
                  (let [[k v] node]
                    (if (contains? redactable-config-vars k)
                      ;; We only consider strings and similar because
                      ;; other vals should immediately cause an assertion
                      ;; error and never be meaningful passwords.
                      (cond
                        (string? v)  [k "[REDACTED]"]
                        (keyword? v) [k :redacted]
                        (symbol? v)  [k 'redacted]
                        :else        [k v])
                      [k v]))
                  node))
              config))

(defmacro assert-config
  [spec component-name config]
  `(when-some [err# (s/explain-data ~spec ~(redact-config-vars config))]
     (do
       (log/errorf "Configuration errors:\n%s"
                   (with-out-str (s/explain-out err#)))
       (throw (ex-info (format "Invalid %s configuration!"
                               ~component-name)
                       {:type ::invalid-config
                        :error-data err#})))))

(comment ; TODO: Turn these into tests
  (s/explain :lrsql.spec.config/database
             (redact-config-vars {:db-type     "h2:mem"
                             :db-name     "foo"
                             :db-password 100}))
  (assert-config :lrsql.spec.config/database
                 "database"
                 {:db-type     "h2:mem"
                  :db-name     "foo"
                  :db-password "swordfish"}))

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
