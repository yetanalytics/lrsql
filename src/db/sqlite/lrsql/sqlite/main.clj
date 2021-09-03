(ns lrsql.sqlite.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.sqlite.record :as sr])
  (:gen-class))

(def sqlite-backend (sr/map->SQLiteBackend {}))

(defn run-test-sqlite
  "Run a SQLite-backed LRSQL instance based on the `:test-sqlite`
   config profile. For use with `clojure -X:db-sqlite`."
  [_] ; Need to pass in a map for -X
  (component/start (system/system sqlite-backend :test-sqlite)))

(defn -main [& _args]
  (-> (system/system sqlite-backend :prod-sqlite)
      component/start))
