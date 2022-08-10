(ns lrsql.init.log
  (:require [clojure.string :as cs])
  (:import [ch.qos.logback.classic Logger Level]
           [org.slf4j LoggerFactory]))

(defn set-log-level!
  "Set logging to the desired level or throw."
  [level]
  (.setLevel
   ^Logger (LoggerFactory/getLogger (Logger/ROOT_LOGGER_NAME))
   (case (cs/upper-case level)
     "OFF"   Level/OFF
     "ERROR" Level/ERROR
     "WARN"  Level/WARN
     "INFO"  Level/INFO
     "DEBUG" Level/DEBUG
     "TRACE" Level/TRACE
     "ALL"   Level/ALL
     (throw (ex-info (format "Unknown LRSQL_LOG_LEVEL: %s" level)
                     {:type  ::unknown-log-level
                      :level level})))))
