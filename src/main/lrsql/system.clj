(ns lrsql.system
  (:require [aero.core :as aero]
            [com.stuartsierra.component :as component]
            [lrsql.system.database :as db]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword (or `:default` if not present)."
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
         config ; TODO: Switch to io/resource
         (aero/read-config "config.edn" {:profile profile})
         assoc-config
         (fn [m config-m] (assoc m :config config-m))]
     (-> (merge-with assoc-config initial-sys config)
         (component/system-using {})))))
