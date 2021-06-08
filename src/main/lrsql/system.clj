(ns lrsql.system
  (:require #_[config.core :refer [env]]
            [aero.core :as aero]
            [next.jdbc.connection :as connection]
            [com.stuartsierra.component :as component]
            [lrsql.system.connection :as conn]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

;; TODO: assert that db-type exists

;; TODO: Add SQLite and Postgres at the very least
(def valid-db-types #{"h2" "h2:mem"})

(defn- assert-db-type
  "Assert that `db-type` is valid."
  [db-type]
  (cond
    (not (some? db-type))
    (throw (ex-data "db-type is nil or not set!"
                    {:type    ::missing-db-type
                     :db-type nil}))
    (not (valid-db-types db-type))
    (throw (ex-data "db-type is invalid!"
                    {:type    ::invalid-db-type
                     :db-type db-type}))
    :else
    nil))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword (or `:default` if not present)."
  ([]
   (system :default))
  ([profile]
   (let [initial-sys ; init without configuration
         (component/system-map
          :connection (component/using
                       (conn/map->Connection {})
                       [])
          :lrs       (component/using
                      (lrs/map->LearningRecordStore {})
                      [:connection])
          :webserver (component/using
                      (webserver/map->Webserver {})
                      [:lrs]))
         config
         (aero/read-config "config.edn" {:profile profile})]
     (-> (merge-with (fn [m cm] (assoc m :config cm)) initial-sys config)
         (component/system-using {})))))
