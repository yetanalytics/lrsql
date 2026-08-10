(ns lrsql.mariadb.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.system.util :as su]
            [lrsql.mariadb.record :as md])
  (:gen-class))

(def mariadb-backend (md/map->MariadbBackend {}))

(defn run-test-mariadb
  "Run a MariaDB-backed LRSQL instance based on the `:test-mariadb`
   config profile. For use with `clojure -X:db-mariadb`."
  [_] ; Need to pass in a map for -X
  (-> (system/system mariadb-backend :test-mariadb)
      component/start
      su/add-shutdown-hook!))

(defn -main [& _args]
  (-> (system/system mariadb-backend :prod-mariadb)
      component/start
      su/add-shutdown-hook!))
