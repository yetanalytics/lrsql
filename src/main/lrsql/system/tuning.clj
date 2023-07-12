(ns lrsql.system.tuning
  (:require [com.stuartsierra.component :as component]))

(defrecord Tuning [config]
  component/Lifecycle
  (start [this]
    this)
  (stop [this] this))
