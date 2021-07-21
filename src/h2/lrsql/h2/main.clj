(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(def h2-interface (ir/map->H2Interface {}))

;; TODO: Add an option to pass `:prod-h2` instead, for persistent instead of
;; ephemeral H2 DB.
;; This should be passed as an arg to the main function, though I'm not sure
;; about main arg best practices in Clojure.
(defn -main [& _]
  (-> (system/system h2-interface :prod-h2-mem)
      component/start))
