(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(defn -main [& _]
  (-> (system/h2-system)
      component/start))
