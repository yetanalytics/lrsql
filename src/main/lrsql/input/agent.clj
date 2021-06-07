(ns lrsql.input.agent
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.actor :as hs]
            [lrsql.util.actor :refer [actor->ifi]]))

(s/fdef agent-query-input
  :args (s/cat :params hs/get-agent-params)
  :ret hs/agent-query-spec)

(defn agent-query-input
  "Construct an input for `query-agent!`"
  [{agent :agent}]
  {:actor-ifi  (actor->ifi agent)
   :actor-type "Agent"}) ; Cannot query Groups
