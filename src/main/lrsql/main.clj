(ns lrsql.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system])
  (:gen-class))

#_{:clj-kondo/ignore [:unused-binding]}
(defn -main [& args]
  (-> (system/system)
      component/start))
