(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]
            [lrsql.ops.util.reaction :as ur]
            [clojure.tools.logging :as log]))

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

(s/fdef query-statement-reactions
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/query-statement-reactions-input-spec)
  :ret rs/query-statement-reactions-ret-spec)

(defn query-statement-reactions
  "Given a statement ID, produce any reactions to that statement."
  [bk tx {:keys [trigger-id]}]
  (let [{statement :payload}  (bp/-query-statement
                               bk tx {:statement-id trigger-id})
        {s-reactions :result} (ur/query-reaction-history
                               bk tx {:statement-id trigger-id})
        active-reactions      (ur/query-active-reactions bk tx)]
    {:result
     (into []
           (for [{:keys       [ruleset]
                  reaction-id :id} active-reactions
                 ;; Prevent cycling
                 :let
                 [cycle-found? (contains? s-reactions reaction-id)
                  _
                  (when cycle-found?
                    (log/warnf
                     "Reaction %s found in statement %s history, ignoring!"
                     reaction-id trigger-id))]
                 :when             (not cycle-found?)
                 ;; Extract statement identity
                 :let
                 [{:keys [identity-paths
                          template]} ruleset
                  statement-identity (ru/statement-identity
                                      identity-paths statement)]
                 :when             statement-identity
                 ;; Look for condition matches
                 ruleset-match     (ur/query-reaction
                                    bk
                                    tx
                                    {:ruleset            ruleset
                                     :trigger-id         trigger-id
                                     :statement-identity statement-identity})]
             (ru/add-reaction-metadata
              (ru/generate-statement
               ruleset-match
               template)
              reaction-id
              trigger-id)))}))
