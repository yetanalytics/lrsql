(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (def sys (system/system))
  (def sys' (component/start sys))

  (component/stop sys'))
