(ns lrsql.system.database
  (:require [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [com.stuartsierra.component :as component]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn- coerce-conn-config
  [conn-config]
  (assert-config ::cs/connection "connection" conn-config)
  (let [{{db-type   :db-type
          db-name   :db-name
          host      :db-host
          port      :db-port
          schema    :db-schema
          ?jdbc-url :db-jdbc-url}
         :database
         ?user      :user
         ?password  :password
         ?init-size :pool-init-size
         ?min-size  :pool-min-size
         ?inc       :pool-inc
         ?max-size  :pool-max-size
         ?max-stmt  :pool-max-stmts}
        conn-config]
    (cond-> {}
      ;; Basic specs
      ?jdbc-url
      (assoc :jdbcUrl ?jdbc-url)
      (not ?jdbc-url)
      (assoc :dbtype db-type
             :dbname db-name
             :host   host
             :port   port
             #_:schema #_schema)
      ;; Additional specs
      ?user
      (assoc :user ?user)
      ?password
      (assoc :password ?password)
      ?init-size
      (assoc :initialPoolSize ?init-size)
      ?min-size
      (assoc :minPoolSize ?min-size)
      ?inc
      (assoc :acquireIncrement ?inc)
      ?max-size
      (assoc :maxPoolSize ?max-size)
      ?max-stmt
      (assoc :maxStatements ?max-stmt))))

(defrecord Connection [conn-pool config]
  component/Lifecycle
  (start
    [conn]
    (let [{?conn-pool :conn-pool
           {{db-type :db-type} :database :as config} :config}
          conn]
      (if-not ?conn-pool
        (let [conn-pool (jdbc-conn/->pool ComboPooledDataSource
                                          (coerce-conn-config config))]
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
        (.close ^ComboPooledDataSource conn-pool)
        (assoc conn :conn-pool nil))
      (do
        (log/info "Connection already stopped; do nothing.")
        conn))))
