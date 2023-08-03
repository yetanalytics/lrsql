(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]
            [lrsql.ops.util.reaction :as ur]
            [xapi-schema.spec :as xs]
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
           (mapcat
            (fn [{:keys       [ruleset]
                  reaction-id :id}]
              ;; Cycle Check
              (if (contains? s-reactions reaction-id)
                (do
                  (log/warnf
                   "Reaction %s found in statement %s history, ignoring!"
                   reaction-id trigger-id)
                  []) ;; ignore
                (let [{:keys [identity-paths
                              template]} ruleset
                      statement-identity (ru/statement-identity
                                          identity-paths statement)]
                  (if-not statement-identity
                    [] ;; ignore
                    (let [[q-success ?q-result]
                          (try
                            [true (ur/query-reaction
                                   bk
                                   tx
                                   {:ruleset            ruleset
                                    :trigger-id         trigger-id
                                    :statement-identity statement-identity})]
                            (catch Exception ex
                              (log/errorf
                               ex
                               "Reaction Query Error - Reaction ID: %s"
                               reaction-id)
                              [false]))]
                      (if (false? q-success)
                        ;; Query Error
                        [{:reaction-id reaction-id
                          :trigger-id  trigger-id
                          :error       :lrsql.reaction.error/query}]
                        (for [ruleset-match ?q-result
                              :let
                              [[t-success ?t-result]
                               (try
                                 [true (ru/generate-statement
                                        ruleset-match
                                        template)]
                                 (catch Exception ex
                                   (log/errorf
                                    ex
                                    "Reaction Template Error - Reaction ID: %s"
                                    reaction-id)
                                   [false]))]]
                          (if (false? t-success)
                            ;; Template Error
                            {:reaction-id reaction-id
                             :trigger-id  trigger-id
                             :error
                             :lrsql.reaction.error/template}
                            (let [statement   ?t-result
                                  valid?      (s/valid? ::xs/statement
                                                        statement)]
                              (if-not valid?
                                ;; Invalid Statement Error
                                (do
                                  (log/errorf
                                   "Reaction Query Error - Reaction ID: %s Spec Error: %s"
                                   reaction-id
                                   (s/explain-str ::xs/statement statement))
                                  {:reaction-id reaction-id
                                   :trigger-id  trigger-id
                                   :error
                                   :lrsql.reaction.error/invalid-statement})
                                {:reaction-id reaction-id
                                 :trigger-id  trigger-id
                                 :statement   statement}))))))))))
            active-reactions))}))
