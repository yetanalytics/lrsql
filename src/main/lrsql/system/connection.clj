(ns lrsql.system.connection
  (:require [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [com.stuartsierra.component :as component])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

;; TODO: Add SQLite and Postgres at the very least
(def valid-db-types #{"h2" "h2:mem"})

(defn- assert-db-type
  "Assert that `db-type` is valid."
  [db-type]
  (cond
    (not (some? db-type))
    (throw (ex-info "db-type is nil or not set!"
                    {:type    ::missing-db-type
                     :db-type nil}))
    (not (valid-db-types db-type))
    (throw (ex-info "db-type is invalid!"
                    {:type    ::invalid-db-type
                     :db-type db-type}))
    :else
    nil))

(defn- coerce-conn-spec
  [conn-spec]
  (let [{{db-type   :db-type
          db-name   :db-name
          host      :host
          port      :port
          schema    :schema
          ?jdbc-url :jdbc-url}
         :database
         ?user      :user
         ?password  :password
         ?init-size :pool-init-size
         ?min-size  :pool-min-size
         ?inc       :pool-inc
         ?max-size  :pool-max-size
         ?max-stmt  :pool-max-stmts}
        conn-spec]
    (assert-db-type db-type)
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
  (component/start
   [conn]
   (let [{?conn-pool :conn-pool
          {{db-type :db-type} :database :as config} :config}
         conn]
     (if-not ?conn-pool
       (let [conn-pool (jdbc-conn/->pool ComboPooledDataSource
                                         (coerce-conn-spec config))]
         (log/infof "Starting new connection for %s database..." db-type)
         (log/tracef "Config: %s" config)
         (assoc conn :conn-pool conn-pool))
       (do
         (log/info "Connection already started; do nothing.")
         conn))))
  (component/stop
   [conn]
   (if-some [conn-pool (:conn-pool conn)]
     (do
       (log/info "Stopping connection...")
       (.close conn-pool)
       (assoc conn :conn-pool nil))
     (do
       (log/info "Connection already stopped; do nothing.")
       conn))))
