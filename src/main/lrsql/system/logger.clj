(ns lrsql.system.logger
  (:require [com.stuartsierra.component :as component]
            [lrsql.init.log :refer [set-log-level!]]))

(defrecord Logger [config]
  component/Lifecycle
  (start [this]
    (when-some [level (:log-level config)]
      (set-log-level! level))
    this)
  (stop [this] this))
