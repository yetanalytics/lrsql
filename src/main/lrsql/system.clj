(ns lrsql.system
  (:require [com.stuartsierra.component :as component]
            [lrsql.system.database :as db]
            [lrsql.system.logger :as logger]
            [lrsql.system.tuning :as tuning]
            [lrsql.system.lrs :as lrs]
            [lrsql.system.webserver :as webserver]
            [lrsql.init.config :refer [read-config]]))

(defn system
  "Return a lrsql system with configuration specified by the `profile`
   keyword. Optional kwarg ``conf-overrides`` takes a map where the key is a vec
   of a key location in config map, and the value is an override."
  [backend profile & {:keys [conf-overrides]}]
  (let [profile-config
        (read-config profile)
        config (reduce (fn [agg [key val]]
                         (assoc-in agg key val))
                       profile-config
                       (seq conf-overrides))
        initial-sys ; init without configuration
        (component/system-map
         ;; Logger is required by backend so it happens first
         :logger     (logger/map->Logger {})
         :tuning     (tuning/map->Tuning {})
         :backend    (component/using
                      backend
                      [:logger :tuning])
         :connection (component/using
                      (db/map->Connection {})
                      [:backend])
         :lrs        (component/using
                      (lrs/map->LearningRecordStore {})
                      [:connection :backend])
         :webserver  (component/using
                      (webserver/map->Webserver {})
                      [:lrs]))
        assoc-config
        (fn [m config-m] (assoc m :config config-m))]
    ;; This code can be confusing. What is happening is that the above creates 
    ;; a system map with empty maps and then based on the key of the system, 
    ;; populates the corresponding config (by key) from the overall aero config
    (-> (merge-with assoc-config initial-sys config)
        (component/system-using {}))))
