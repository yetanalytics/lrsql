(ns lrsql.sqlite.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.sqlite.record :as sr])
  (:gen-class))

(def sqlite-backend (sr/map->SQLiteBackend {}))

(defn run-test-sqlite
  "Run a SQLite-backed LRSQL instance based on the `:test-sqlite` (if
  `:ephemeral?` is set to `false`) or `:test-sqlite-mem` (if `true`) config
  profile. For use with `clojure -X:db-sqlite`."
  [{:keys [ephemeral?
           override-profile]}]
  (let [profile (or override-profile
                    (if ephemeral? :test-sqlite-mem :test-sqlite))]
    (component/start (system/system sqlite-backend profile))))

(defn -main
  "Main entrypoint for SQLite-backed LRSQL instances. Passing `--ephemeral true`
   will spin up an in-mem SQLLite instance; otherwise, a persistent SQLite db
   will be stored on disk."
  [& args]
  (let [{?per-str "--ephemeral"} args
        ephemeral?  (Boolean/parseBoolean ?per-str)
        profile     (if ephemeral? :prod-sqlite-mem :prod-sqlite)]
    (-> (system/system sqlite-backend profile)
        component/start)))
