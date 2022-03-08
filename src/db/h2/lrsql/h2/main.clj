(ns lrsql.h2.main
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as hr])
  (:gen-class))

(def h2-backend (hr/map->H2Backend {}))

(defn run-test-h2
  "Run an H2-backed LRSQL instance based on the `:test-h2` (if `:persistent?`
   is set to `true`) or `:test-h2-mem` (if not) config profile. For use with
   `clojure -X:db-h2`."
  [{:keys [persistent?
           override-profile]}]
  (let [profile (or override-profile
                  (if persistent? :test-h2 :test-h2-mem))]
    (component/start (system/system h2-backend profile))))

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
