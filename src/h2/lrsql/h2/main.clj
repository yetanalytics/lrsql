(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(def h2-interface (ir/map->H2Interface {}))

(defn -main [& _]
  (-> (system/system h2-interface)
      component/start))
