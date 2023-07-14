(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]))

(s/fdef query-reaction
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input ::rs/input))

(defn query-reaction
  [bk tx {:keys [conditions]}]
  (let [condition-keys (keys conditions)]
    (bp/-query-reaction
     bk
     tx
     {:select (mapv
               (fn [k]
                 [(format "%s.payload" (name k)) (name k)])
               condition-keys)
      :from (mapv
             (fn [k]
               ["xapi_statement" (name k)])
             condition-keys)
      :where
      (bp/-snip-and
       {:clauses
        []})})))
