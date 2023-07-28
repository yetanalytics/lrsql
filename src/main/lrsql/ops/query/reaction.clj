(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]

            [lrsql.ops.util.reaction :as ur]))

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
  :ret rs/react-to-statement-ret-spec)

(defn query-statement-reactions
  "Given a statement ID, produce any reactions to that statement."
  [bk tx {:keys [trigger-id]}]
  (let [statement (-> (bp/-query-statement bk tx {:statement-id trigger-id})
                      :payload
                      u/parse-json)]
    {:result
     (into []
           ;; Cycle check could happen in the active reactions query
           (for [{:keys [ruleset]} (ur/query-active-reactions bk tx)
                 :let
                 [{:keys [identity-paths]} ruleset
                  statement-identity (ru/statement-identity
                                      identity-paths statement)]
                 :when             statement-identity
                 ;; And/or here
                 ruleset-match     (ur/query-reaction
                                    bk
                                    tx
                                    {:ruleset            ruleset
                                     :trigger-id         trigger-id
                                     :statement-identity statement-identity})]
             (comment
               ;; yields a statement
               (process-template template ruleset-match)
               )))}))
