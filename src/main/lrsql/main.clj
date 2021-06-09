(ns lrsql.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system])
  (:gen-class))

(defn -main [& _]
  (-> (system/system)
      component/start))
