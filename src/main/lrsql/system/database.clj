(ns lrsql.system.database
  (:require [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [com.stuartsierra.component :as component]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config parse-db-props]])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]
           [com.codahale.metrics MetricRegistry]
           [com.codahale.metrics.jmx JmxReporter]))

(set! *warn-on-reflection* true)

(defn- make-jdbc-url
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

;; Note: there is a hikari-cp wrapper lib for Clojure. However, we skip using
;; this because 1) it uses HikariCP v4 (which is for Java 8, not Java 11+) and
;; 2) it doesn't set several properties that the latest version supports.

(defn- enable-jmx!
  [^HikariConfig config]
  (let [metric-registry (MetricRegistry.)]
    ;; Set HikariCP config properties
    ;; NOTE: we skip setting the healthCheckRegistry since enough info
    ;; should be already provided by the metrics, and there doesn't seem
    ;; to be an easy way to call its properties via JMX.
    (.setRegisterMbeans config true)
    (.setAllowPoolSuspension config true)
    (.setMetricRegistry config metric-registry)
    ;; Add the metric registry to the JMX reporter
    ;; Code from Pedestal:
    ;; https://github.com/pedestal/pedestal/blob/master/log/src/io/pedestal/log.clj#L489
    (doto (some-> (JmxReporter/forRegistry metric-registry)
                  (.inDomain "com.zaxxer.hikari.metrics")
                  (.build))
      (.start))))

(defn- make-conn-pool
  [;; Backend
   backend
   ;; Config
   {{:keys [db-user
            db-password
            db-schema
            db-catalog]
     :as db-config}
    :database
    :keys [pool-auto-commit
           pool-keepalive-time
           pool-connection-timeout
           pool-idle-timeout
           pool-validation-timeout
           pool-initialization-fail-timeout
           pool-max-lifetime
           pool-minimum-idle
           pool-maximum-size
           pool-isolate-internal-queries
           pool-leak-detection-threshold
           pool-transaction-isolation
           pool-enable-jmx
           pool-name]
    :as conn-config}]
  (assert-config ::cs/connection "connection" conn-config)
  (let [conf (doto (HikariConfig.)
               ;; Database properties
               (.setJdbcUrl  (make-jdbc-url db-config))
               (.setUsername db-user)
               (.setPassword db-password)
               (.setSchema   db-schema)
               (.setCatalog  db-catalog)
               ;; Connection pool properties
               (.setAutoCommit                pool-auto-commit)
               (.setKeepaliveTime             pool-keepalive-time)
               (.setConnectionTimeout         pool-connection-timeout)
               (.setIdleTimeout               pool-idle-timeout)
               (.setValidationTimeout         pool-validation-timeout)
               (.setInitializationFailTimeout pool-initialization-fail-timeout)
               (.setMaxLifetime               pool-max-lifetime)
               (.setMinimumIdle               pool-minimum-idle)
               (.setMaximumPoolSize           pool-maximum-size)
               (.setIsolateInternalQueries    pool-isolate-internal-queries)
               (.setLeakDetectionThreshold    pool-leak-detection-threshold))]
    ;; Why is there no conditional doto?
    (when pool-name
      (.setPoolName conf pool-name))
    (when pool-transaction-isolation
      (.setTransactionIsolation conf pool-transaction-isolation))
    (when-some [init-sql (bp/-conn-init-sql backend)]
      (.setConnectionInitSql conf init-sql))
    (when pool-enable-jmx
      (enable-jmx! conf))
    ;; Make connection pool/datasource
    (HikariDataSource. conf)))

(defrecord Connection [backend conn-pool config]
  component/Lifecycle
  (start
    [conn]
    (let [{?conn-pool :conn-pool
           {{db-type :db-type} :database :as config} :config}
          conn]
      (if-not ?conn-pool
        (let [conn-pool (make-conn-pool backend config)]
          (log/infof "Starting new connection for %s database..." db-type)
          (log/tracef "Config: %s" config)
          (assoc conn :conn-pool conn-pool))
        (do
          (log/info "Connection already started; do nothing.")
          conn))))
  (stop
    [conn]
    (if-some [conn-pool (:conn-pool conn)]
      (do
        (log/info "Stopping connection...")
        (.close ^HikariDataSource conn-pool)
        (assoc conn :conn-pool nil))
      (do
        (log/info "Connection already stopped; do nothing.")
        conn))))
