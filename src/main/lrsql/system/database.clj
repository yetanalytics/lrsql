(ns lrsql.system.database
  (:require [clojure.string :as cstr]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [next.jdbc :refer [with-logging]]
            [com.stuartsierra.component :as component]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn- parse-db-props
  "Given `prop-str` of the form \"key:value,key:value,...\", return a
   keyword-key map of property names to values."
  [prop-str]
  (->> (cstr/split prop-str #",")
       (mapv #(cstr/split % #":"))
       (into {})
       keywordize-keys))

(defn- coerce-conn-config
  [conn-config]
  (assert-config ::cs/connection "connection" conn-config)
  (let [{{db-type   :db-type
          db-name   :db-name
          host      :db-host
          port      :db-port
          ?user     :db-user
          ?password :db-password
          ?props    :db-properties
          ?jdbc-url :db-jdbc-url}
         :database
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
      (assoc :jdbcUrl (cond-> {:dbtype db-type
                               :dbname db-name
                               :host   host
                               :port   port}
                        ?props
                        (merge (parse-db-props ?props))
                        true
                        jdbc-conn/jdbc-url))
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

(defn log-ds
  [ds]
  (with-logging ds
    (fn [sym sql-params]
      (prn sym sql-params)
      (System/currentTimeMillis))
    (fn [sym state result]
      (prn sym
           (- (System/currentTimeMillis) state)
           (if (map? result) result (count result))))))


(defrecord Connection [conn-pool config]
  component/Lifecycle
  (start
    [conn]
    (let [{?conn-pool :conn-pool
           {{db-type :db-type} :database :as config} :config}
          conn]
      (if-not ?conn-pool
        (let [conn-pool (log-ds
                         (jdbc-conn/->pool ComboPooledDataSource
                                           (coerce-conn-config config)))]
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
