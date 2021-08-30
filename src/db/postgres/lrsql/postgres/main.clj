(ns lrsql.postgres.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.postgres.record :as pr])
  (:gen-class))

(def postgres-backend (pr/map->PostgresBackend {}))

(defn -main [& _args]
  (-> (system/system postgres-backend :prod-postgres)
      component/start))
