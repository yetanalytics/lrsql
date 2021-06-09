(ns lrsql.system
  (:require [com.stuartsierra.component :as component]
            [lrsql.system.database :as db]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]
            [lrsql.util :as u]))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword (set to `:default` if not given)."
  ([]
   (system :default))
  ([profile]
   (let [initial-sys ; init without configuration
         (component/system-map
          :connection (component/using
                       (db/map->Connection {})
                       [])
          :lrs       (component/using
                      (lrs/map->LearningRecordStore {})
                      [:connection])
          :webserver (component/using
                      (webserver/map->Webserver {})
                      [:lrs]))
         config
         (u/read-config profile)
         assoc-config
         (fn [m config-m] (assoc m :config config-m))]
     (-> (merge-with assoc-config initial-sys config)
         (component/system-using {})))))
