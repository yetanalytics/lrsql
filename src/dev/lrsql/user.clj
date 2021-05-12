(ns lrsql.user
  "Namespace to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.hugsql.init :as init]
            [lrsql.system :as system]
            [lrsql.hugsql.functions :as f]))

(comment
  (def sys (system/system))

  (component/start sys)

  (component/stop sys)
  
  (init/init-hugsql-fns! "h2")
  (f/create-agent-table-sqlvec))
