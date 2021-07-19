(ns lrsql.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.init :refer [init-aot-compilation]]
            [lrsql.util :refer [read-config]])
  (:gen-class))

(defn- init-aot
  "Init the HugSql adapter, DB functions, and DB interface protocols
   during AOT compilation, based on `:db-type` in the config file."
  []
  (let [db-type (-> (read-config :default) :database :db-type)]
    (init-aot-compilation db-type)))

(init-aot)

(defn -main [& _]
  (-> (system/system)
      component/start))
