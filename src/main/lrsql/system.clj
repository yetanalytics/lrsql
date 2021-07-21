(ns lrsql.system
  (:require [com.stuartsierra.component :as component]
            [lrsql.system.database :as db]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]
            [lrsql.util :as u]))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword."
  [iface profile]
  (let [config
        (u/read-config profile)
        db-type
        (-> config :database :db-type)
        initial-sys ; init without configuration
        (component/system-map
         :connection (component/using
                      (db/map->Connection {})
                      [])
         :interface  (component/using
                      (cond
                        (#{"h2" "h2:mem"} db-type)
                        iface)
                      [])
         :lrs        (component/using
                      (lrs/map->LearningRecordStore {})
                      [:connection :interface])
         :webserver  (component/using
                      (webserver/map->Webserver {})
                      [:lrs]))
        assoc-config
        (fn [m config-m] (assoc m :config config-m))]
    (-> (merge-with assoc-config initial-sys config)
        (component/system-using {}))))
