(ns lrsql.system.database
  (:require [clojure.string :as cstr]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.tools.logging :as log]
            [next.jdbc.connection :as jdbc-conn]
            [com.stuartsierra.component :as component]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn- assert-db-type
  "Ensure that the correct \"src/sql/...\" directory (and by extension,
   the appropriate deps for the DBMS) are loaded. Throws exceptions if
   multiple dirs are loaded, or if the dir for `db-type` is not."
  [db-type]
  ;; There doesn't seem to be an easy way to check Clojure aliases, and
  ;; checking for Java deps is weird, so use the hacky method of checking
  ;; the classpath.
  (let [db-type'  (if (#{"h2:mem"} db-type) "h2" db-type)
        db-path   (str "src/sql/" db-type')
        ;; Use approach from clojure.java.classpath/system-classpath
        cp-paths  (seq (.split (System/getProperty "java.class.path")
                               (System/getProperty "path.separator")))
        sql-paths (filterv (partial re-matches #"src/sql/.*") cp-paths)]
    (cond
      (< 1 (count sql-paths))
      (throw (ex-info "Multiple \"src/sql/...\" directories loaded!"
                      {:type      ::multiple-sql-paths
                       :db-type   db-type
                       :sql-paths sql-paths}))

      (not (some #{db-path} sql-paths))
      (throw (ex-info (format "Directory \"%s\" not loaded!" db-path)
                      {:type      ::missing-sql-path
                       :db-type   db-type
                       :sql-paths sql-paths}))

      :else
      nil)))

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
  (assert-db-type (-> conn-config :database :db-type))
  (let [{{db-type   :db-type
          db-name   :db-name
          ?host     :db-host
          ?port     :db-port
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
                               :dbname db-name}
                        ?host
                        (assoc :host ?host)
                        ?port
                        (assoc :port ?port)
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
