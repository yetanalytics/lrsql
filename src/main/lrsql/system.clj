(ns lrsql.system
  (:require [config.core :refer [env]]
            [clojure.string :as cstr]
            [clj-uuid]
            [next.jdbc.connection :as connection]
            [com.stuartsierra.component :as component]
            [lrsql.hugsql.init :as init]
            [lrsql.lrs :as lrs])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn db-spec
  "Derive the spec for `connection/component` based off of `env`."
  []
  (let [{db-type   :db-type
         db-name   :db-name
         host      :db-host
         port      :db-port
         schema    :db-schema
         jdbc-url  :db-jdbc-url
         user      :db-user
         password  :db-password
         init-size :db-init-size
         min-size  :db-min-size
         inc       :db-inc
         max-size  :db-max-size
         max-stmt  :db-max-stmt}
        env
        basic-specs
        (if jdbc-url
          {:jdbcUrl jdbc-url}
          {:dbtype db-type
           :dbname db-name
           :host   host
           :port   port
           #_:schema #_schema})]
    (cond-> basic-specs
      user
      (assoc :user user)
      password
      (assoc :password password)
      init-size
      (assoc :initialPoolSize init-size)
      min-size
      (assoc :minPoolSize min-size)
      inc
      (assoc :acquireIncrement inc)
      max-size
      (assoc :maxPoolSize max-size)
      max-stmt
      (assoc :maxStatements max-stmt))))

(defn- get-db-type
  "Get the db-type. Assumes that JDBC URLs start with protocols of
   the format `jdbc:<db-type>`."
  []
  (if-let [db-type (:db-type env)]
    db-type
    (let [jdbc-url  (:db-jdbc-url env)
          split-url (cstr/split jdbc-url #":")]
      (assert (= "jdbc" (first split-url)))
      (second split-url))))

(defn- pool-component
  "Return a connection pool component."
  []
  (connection/component ComboPooledDataSource (db-spec)))

#_(defrecord Database [db-type conn-pool]
  component/Lifecycle
  (start [component]
    (init/init-hugsql-adapter!)
    (init/init-hugsql-fns! db-type)
    (init/create-tables! (conn-pool))
    (assoc component :conn-pool conn-pool))
  (stop [component]
    (dissoc component :conn-pool conn-pool)))

(defn system
  "A thunk that returns a lrsql system when called."
  []
  (component/system-map
   :conn-pool (pool-component)
   :lrs (component/using
        (lrs/map->LearningRecordStore {:db-type (get-db-type)})
        [:conn-pool])))
