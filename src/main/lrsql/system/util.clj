(ns lrsql.system.util
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [clojure.string :as cstr]
            [clojure.walk :as w]
            [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [lrsql.util :refer [form-encode form-decode]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers and Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def redactable-config-vars
  #{:db-password
    :admin-pass-default
    :api-secret-default
    :key-password})

(defn- redact-at-node
  [node]
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

(defn redact-config-vars
  "Given a `config` map, replace redactable values with \"[REDACTED]\"
   (if it is a string; keywords and symbols are treated similarly).
   See `redactable-config-vars` for which vars are redactable."
  [config]
  (w/postwalk redact-at-node config))

;; `explain-data` holds every value it checked (each problem's `:val`, and the
;; whole map as `::s/value`), secrets included, so neither the log nor the
;; thrown exception carries it. They carry, for each problem, only the key path
;; (`:in`), the predicate that failed and spec's reason, which come from the
;; spec and not from the configuration.

(defn config-problems
  "Given the `explain-data` of a rejected configuration, return its problems
   as key paths, failed predicates and reasons, with no configuration values."
  [explain-data]
  (mapv (fn [{:keys [in pred reason]}]
          (cond-> {:in in :pred (s/abbrev pred)}
            reason (assoc :reason reason)))
        (::s/problems explain-data)))

(defn format-config-problem
  "Format one problem from `config-problems` as a log line."
  [{:keys [in pred reason]}]
  (format "  at %s: failed %s%s" (pr-str in) (pr-str pred)
          (if reason (str " (" reason ")") "")))

(defmacro assert-config
  "Check `config` against `spec`. If it does not conform, log and throw the
   key path and failed check of each problem, never the configuration's
   values."
  [spec component-name config]
  `(when-some [err# (s/explain-data ~spec ~config)]
     (let [problems# (config-problems err#)]
       (log/errorf "Configuration errors:\n%s"
                   (cstr/join "\n" (map format-config-problem problems#)))
       (throw (ex-info (format "Invalid %s configuration!"
                               ~component-name)
                       {:type     ::invalid-config
                        :problems problems#})))))

(defn config-key-paths
  "Return the key paths of `config` that hold a non-nil value, sorted, and
   none of the values."
  [config]
  (letfn [(paths [prefix x]
            (cond
              (map? x) (mapcat (fn [[k v]] (paths (conj prefix k) v)) x)
              (nil? x) []
              :else    [prefix]))]
    (sort-by pr-str (paths [] config))))

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
    (jdbc-conn/jdbc-url
     (cond-> {:dbtype db-type
              :dbname db-name
              :host   db-host
              :port   db-port}
       db-properties
       (merge (parse-db-props db-properties))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graceful Shutdown
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-shutdown-hook!
  "Given a running system, add a shutdown hook to gracefully stop it."
  [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable
                             (fn []
                               (component/stop system))))
  system)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component-redacted Startup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-system
  [system]
  (try
    (component/start system)
    (catch Throwable error
      (throw (component/ex-without-components error)))))

;; An uncaught startup exception reaches the JVM's default handler, which
;; prints each ex-info with its data, and that data can hold configuration
;; values, secrets included. `start-system-or-exit` logs only what the code
;; wrote: the classes, the messages (never the data), a keyword `:type` and
;; the failing component's key.

(def startup-logger-name
  "The logger that reports a failed startup."
  "lrsql.startup")

(defn- startup-cause-text
  [^Throwable t]
  (let [t-type (:type (ex-data t))
        msg    (some-> (.getMessage t) (cstr/replace #"\s+" " ") cstr/trim)]
    (str (.getName (class t))
         (when (keyword? t-type) (str " type=" t-type))
         (when (seq msg)
           (str ": " (if (< 300 (count msg)) (str (subs msg 0 300) "...") msg))))))

(defn startup-failure-line
  "Describe the startup failure `error` in one line, from its cause chain's
   classes, messages and keyword `:type`s and the `:system-key` of the
   component that failed. No exception data and no `toString` of an ex-info."
  [^Throwable error]
  (let [chain (take 16 (take-while some? (iterate #(.getCause ^Throwable %) error)))
        sys-k (some (fn [t] (let [k (:system-key (ex-data t))]
                              (when (keyword? k) k)))
                    chain)]
    (str "Startup failed"
         (when sys-k (str " in component " sys-k))
         ": "
         (cstr/join " <- " (map startup-cause-text chain)))))

(defn- exit!
  [status]
  (System/exit status))

(defn start-system-or-exit
  "Make a system with `make-system` (a no-argument function) and start it with
   `start-system`. If either throws, log one ERROR line from
   `startup-failure-line` and exit with status 1."
  [make-system]
  (try
    (start-system (make-system))
    (catch Throwable error
      (log/log startup-logger-name :error nil (startup-failure-line error))
      (exit! 1))))
