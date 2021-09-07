(ns lrsql.sqlite.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.sqlite.record :as sr])
  (:gen-class))

(def sqlite-backend (sr/map->SQLiteBackend {}))

(defn -main [& _args]
  (-> (system/system sqlite-backend :prod-sqlite)
      component/start))
