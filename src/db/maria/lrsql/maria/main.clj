(ns lrsql.maria.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.system.util :as su]
            [lrsql.maria.record :as md])
  (:gen-class))

(def maria-backend (md/map->MariaBackend {}))

(defn run-test-maria
  "Run a MariaDB-backed LRSQL instance based on the `:test-maria`
   config profile. For use with `clojure -X:db-mariadb`."
  [_] ; Need to pass in a map for -X
  (-> (system/system maria-backend :test-maria)
      component/start
      su/add-shutdown-hook!))

(defn -main [& _args]
  (-> (system/system maria-backend :prod-maria)
      component/start
      su/add-shutdown-hook!))
