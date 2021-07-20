(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(defn -main [& _]
  (-> (system/system (ir/map->H2Interface {}))
      component/start))
