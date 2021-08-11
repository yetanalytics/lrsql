(ns lrsql.system
  (:require [com.stuartsierra.component :as component]
            [lrsql.system.database :as db]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]
            [lrsql.init.config :refer [read-config]]))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword."
  [backend profile]
  (let [config
        (read-config profile)
        initial-sys ; init without configuration
        (component/system-map
         :backend    (component/using
                      backend
                      [])
         :connection (component/using
                      (db/map->Connection {})
                      [])
         :lrs        (component/using
                      (lrs/map->LearningRecordStore {})
                      [:connection :backend])
         :webserver  (component/using
                      (webserver/map->Webserver {})
                      [:lrs]))
        assoc-config
        (fn [m config-m] (assoc m :config config-m))]
    (-> (merge-with assoc-config initial-sys config)
        (component/system-using {}))))
