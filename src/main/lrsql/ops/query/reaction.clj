(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util.reaction :as ru]
            [lrsql.ops.util.reaction :as ur]
            [lrsql.util :as u]
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
                title
                ruleset
                active
                created
                modified
                error]}]
     {:id       id
      :title    title
      :ruleset  (ru/stringify-template ruleset)
      :active   active
      :created  (u/time->str created)
      :modified (u/time->str modified)
      :error    error})
   (bp/-query-all-reactions bk tx)))

(s/fdef query-statement-reactions
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/query-statement-reactions-input-spec)
  :ret rs/query-statement-reactions-ret-spec)

(defn- reaction-error-response
  "Return a properly formatted reaction error response"
  [reaction-id trigger-id error-type error-message]
  {:reaction-id reaction-id
   :trigger-id  trigger-id
   :error       {:type    error-type
                 :message error-message}})

(defn- reaction-query
  [bk tx ruleset reaction-id trigger-id trigger-stored statement-identity]
  (try
    [true (ur/query-reaction
           bk
           tx
           {:ruleset            ruleset
            :trigger-id         trigger-id
            :trigger-stored     trigger-stored
            :statement-identity statement-identity})]
    (catch Exception ex
      (log/errorf
       ex
       "Reaction Query Error - Reaction ID: %s"
       reaction-id)
      [false (reaction-error-response
              reaction-id
              trigger-id
              "ReactionQueryError"
              (ex-message ex))])))

(defn- generate-statement
  [reaction-id trigger-id ruleset-match template]
  (try
    [true (ru/generate-statement
           ruleset-match
           template)]
    (catch Exception ex
      (log/errorf
       ex
       "Reaction Template Error - Reaction ID: %s"
       reaction-id)
      [false (reaction-error-response
              reaction-id
              trigger-id
              "ReactionTemplateError"
              (ex-message ex))])))

(defn- check-reaction-query
  [bk tx
   {:keys [statement trigger-id statement-identity]
    :as   _opts}
   {:keys       [ruleset]
    reaction-id :id
    :as         _reaction}]
  (let [{:keys [template]}  ruleset
        stored (u/str->time (get statement "stored"))
        [q-success ?q-result-or-error]
        (reaction-query
         bk tx ruleset reaction-id trigger-id stored
         statement-identity)]
    (if (false? q-success)
      ;; Query Error
      [?q-result-or-error]
      (for [ruleset-match ?q-result-or-error
            :let
            [[t-success ?t-result-or-error]
             (generate-statement
              reaction-id trigger-id ruleset-match template)]]
        (if (false? t-success)
          ;; Template Error
          ?t-result-or-error
          (let [new-statement ?t-result-or-error
                valid?        (s/valid? ::xs/statement
                                        new-statement)]
            (if-not valid?
              ;; Invalid Statement Error
              (let [explanation
                    (s/explain-str ::xs/statement new-statement)]
                (log/errorf
                 "Reaction Invalid Statement Error - Reaction ID: %s Spec Error: %s"
                 reaction-id
                 explanation)
                (reaction-error-response
                 reaction-id
                 trigger-id
                 "ReactionInvalidStatementError"
                 (format "Reaction Invalid Statement Error - Spec Error: %s"
                         explanation)))
              ;; Success Response
              {:reaction-id reaction-id
               :trigger-id  trigger-id
               :statement   (ru/add-reaction-metadata
                             new-statement
                             reaction-id
                             trigger-id)
               ;; Use a custom authority from the template or use
               ;; the trigger statement's authority
               :authority   (or (get new-statement "authority")
                                (get statement "authority"))})))))))

(defn- check-reaction [bk tx
                       {:keys [s-reactions
                               statement
                               trigger-id]
                        :as   opts}
                       {:keys       [ruleset]
                        reaction-id :id
                        :as         reaction}]
  ;; Cycle Check
  (if (contains? s-reactions reaction-id)
    (do
      (log/warnf
       "Reaction %s found in statement %s history, ignoring!"
       reaction-id trigger-id)
      []) ;; ignore
    ;; Identity check
    (let [{:keys [identityPaths]} ruleset
          statement-identity      (ru/statement-identity
                                   identityPaths statement)]
      (if-not statement-identity
        [] ;; ignore
        (check-reaction-query
         bk tx
         (assoc opts :statement-identity statement-identity)
         reaction)))))

(defn query-statement-reactions
  "Given a statement ID, produce any reactions to that statement."
  [bk tx {:keys [trigger-id]}]
  (let [active-reactions (ur/query-active-reactions bk tx)]
    ;; If there are no reactions, short-circuit w/o additional queries
    (if (empty? active-reactions)
      {:result []}
      (let [{statement :payload}  (bp/-query-statement
                                   bk tx {:statement-id trigger-id})
            {s-reactions :result} (ur/query-reaction-history
                                   bk tx {:statement-id trigger-id})]
        {:result
         (into
          []
          (mapcat
           (partial check-reaction
                    bk tx
                    {:statement   statement
                     :trigger-id  trigger-id
                     :s-reactions s-reactions})
           active-reactions))}))))
