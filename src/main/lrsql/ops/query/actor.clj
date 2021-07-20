(ns lrsql.ops.query.actor
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.actor :as as]
            [lrsql.util.actor :as au]))

(s/fdef query-agent
  :args (s/cat :inf c/query-interface?
               :tx c/transaction?
               :input as/query-agent-spec)
  :ret ::lrsp/get-person-ret)

(defn query-agent
  "Query an Agent from the DB. Returns a map between `:person` and the
   resulting Person object. Throws an exception if not found. Does not
   query Groups."
  [inf tx input]
  ;; If agent is not found, return the original input
  (let [agent (if-some [{result :payload} (ip/-query-actor inf tx input)]
                result
                (:payload input))]
    {:person (->> agent au/actor->person)}))
