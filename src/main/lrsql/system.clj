(ns lrsql.system
  (:require [config.core :refer [env]]
            [next.jdbc.connection :as connection]
            [com.stuartsierra.component :as component]
            [lrsql.lrs :as lrs])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

;; TODO: assert that db-type exists

(defn db-spec
  "Derive the spec for `connection/component` based off of `env`."
  []
  (let [{db-type   :db-type
         db-name   :db-name
         host      :db-host
         port      :db-port
        ;;  schema    :db-schema ; TODO
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

(defn- pool-component
  "Return a connection pool component."
  []
  (connection/component ComboPooledDataSource (db-spec)))

;; TODO: Add SQLite and Postgres at the very least
(def valid-db-types #{"h2" "h2:mem"})

(defn- assert-db-type
  "Assert that `db-type` is valid."
  [db-type]
  (cond
    (not (some? db-type))
    (throw (ex-data "db-type is nil or not set!"
                    {:kind    ::missing-db-type
                     :db-type nil}))
    (not (valid-db-types db-type))
    (throw (ex-data "db-type is invalid!"
                    {:kind    ::invalid-db-type
                     :db-type db-type}))
    :else
    nil))

(defn system
  "A thunk that returns a lrsql system when called."
  []
  (let [{:keys [db-type]} env]
    (assert-db-type db-type)
    (component/system-map
     :conn-pool (pool-component)
     :lrs (component/using
           (lrs/map->LearningRecordStore {:db-type db-type})
           [:conn-pool]))))
