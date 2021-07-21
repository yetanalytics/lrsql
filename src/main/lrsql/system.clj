(ns lrsql.system
  (:require [com.stuartsierra.component :as component]
            [lrsql.h2.record :as h2-ir]
            [lrsql.system.database :as db]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]
            [lrsql.util :as u]))

(defn system
  "Return a lrsql system with configuration specified the DB interface `inf`
   and by the `profile` keyword (set to `:default` if not given)."
  ([inf]
   (system inf :default))
  ([inf profile]
   (let [config
         (u/read-config profile)
         initial-sys ; init without configuration
         (component/system-map
          :interface  (component/using
                       inf
                       [])
          :connection (component/using
                       (db/map->Connection {})
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
         (component/system-using {})))))

(defn h2-system
  ([]
   (system (h2-ir/map->H2Interface {}) :default))
  ([profile]
   (system (h2-ir/map->H2Interface {}) profile)))
