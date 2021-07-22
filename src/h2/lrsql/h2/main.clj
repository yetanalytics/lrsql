(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(def h2-backend (ir/map->H2Backend {}))

(defn -main
  "Main entrypoint for H2-backed LRSQL instances. Passing `--persistent true`
   will spin up a persistent H2 instance on disk; otherwise, an in-mem,
   ephemeral H2 instance will init instead."
  [& args]
  (let [{?per-str "--persistent"} args
        persistent? (Boolean/parseBoolean ?per-str)
        profile     (if persistent? :prod-h2 :prod-h2-mem)]
    (-> (system/system h2-backend profile)
        component/start)))
