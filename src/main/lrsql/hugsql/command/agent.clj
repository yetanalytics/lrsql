(ns lrsql.hugsql.command.agent
  (:require [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]
            [com.yetanalytics.lrs.xapi.agents :as agnt]))

(defn query-agent
  "Query an Agent from the DB. Returns a map between `:person` and the
   resulting Person object. Throws an exception if not found."
  [tx input]
  ;; If agent is not found, return the original input
  (let [agent (if-some [result (:payload (f/query-agent tx input))]
                result
                (:payload input))]
    {:person (->> agent u/parse-json agnt/person)}))
