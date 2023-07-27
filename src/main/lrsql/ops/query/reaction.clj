(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]))

(s/fdef query-all-reactions
  :args (s/cat :bx rs/reaction-backend?
               :tx transaction?)
  :ret rs/query-all-reactions-ret-spec)

(defn query-all-reactions
  "Return all reactions."
  [bk tx]
  (mapv
   (fn [{:keys [id
                ruleset
                active
                created
                modified]}]
     {:id       id
      :ruleset  (ru/deserialize-ruleset ruleset)
      :active   (= 1 active)
      :created  (u/str->time created)
      :modified (u/str->time modified)})
   (bp/-query-all-reactions bk tx)))
