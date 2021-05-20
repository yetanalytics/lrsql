(ns lrsql.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system])
  (:gen-class))

(defn -main [& args]
  (-> (system/system)
      component/start))
