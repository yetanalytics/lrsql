(ns lrsql.system.database
  (:require [clojure.tools.logging :as log]
            [hikari-cp.core :as hikari]
            [next.jdbc.connection :as jdbc-conn]
            [com.stuartsierra.component :as component]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config parse-db-props]])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]
           [com.codahale.metrics MetricRegistry]
           [com.codahale.metrics.jmx JmxReporter]))

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

(defn- enable-jmx!
  [config]
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
  [{{:keys [db-user
            db-password
            db-schema]
     :as db-config}
    :database
    :keys [pool-auto-commit
           pool-keepalive-time
           pool-connection-timeout
           pool-idle-timeout
           pool-validation-timeout
           pool-init-fail-timeout
           pool-max-lifetime
           pool-min-idle
           pool-max-size
           pool-enable-jmx
           pool-name]
    :as conn-config}]
  (assert-config ::cs/connection "connection" conn-config)
  (let [config (doto (HikariConfig.)
                 ;; Database properties
                 (.setJdbcUrl  (make-jdbc-url db-config))
                 (.setUsername db-user)
                 (.setPassword db-password)
                 (.setSchema   db-schema)
                 ;; Connection pool properties
                 (.setAutoCommit                pool-auto-commit)
                 (.setKeepaliveTime             pool-keepalive-time)
                 (.setConnectionTimeout         pool-connection-timeout)
                 (.setIdleTimeout               pool-idle-timeout)
                 (.setValidationTimeout         pool-validation-timeout)
                 (.setInitializationFailTimeout pool-init-fail-timeout)
                 (.setMaxLifetime               pool-max-lifetime)
                 (.setMinimumIdle               pool-min-idle)
                 (.setMaximumPoolSize           pool-max-size))]
    ;; Why is there no conditional doto?
    (when pool-name       (.setPoolName config pool-name))
    (when pool-enable-jmx (enable-jmx! config))
    ;; Make connection pool/datasource
    (HikariDataSource. config)))

(defrecord Connection [conn-pool config]
  component/Lifecycle
  (start
    [conn]
    (let [{?conn-pool :conn-pool
           {{db-type :db-type} :database :as config} :config}
          conn]
      (if-not ?conn-pool
        (let [conn-pool (make-conn-pool config)]
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
        (hikari/close-datasource conn-pool)
        (assoc conn :conn-pool nil))
      (do
        (log/info "Connection already stopped; do nothing.")
        conn))))
