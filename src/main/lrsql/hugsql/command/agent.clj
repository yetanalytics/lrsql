(ns lrsql.hugsql.command.agent
  (:require [lrsql.hugsql.functions :as f]
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.util.actor :as ua]))

(defn query-agent
  "Query an Agent from the DB. Returns a map between `:person` and the
   resulting Person object. Throws an exception if not found. Does not
   query Groups."
  [tx input]
  ;; If agent is not found, return the original input
  (let [agent (if-some [result (some-> (f/query-actor tx input)
                                       :payload
                                       u/parse-json)]
                result
                (:payload input))]
    {:person (->> agent ua/actor->person)}))
