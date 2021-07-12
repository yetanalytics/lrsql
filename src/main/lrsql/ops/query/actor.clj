(ns lrsql.ops.query.actor
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.functions :as f]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.actor :as as]
            [lrsql.util :as u]
            [lrsql.util.actor :as au]))

(s/fdef query-agent
  :args (s/cat :tx transaction? :input as/query-agent-spec)
  :ret ::lrsp/get-person-ret)

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
    {:person (->> agent au/actor->person)}))
