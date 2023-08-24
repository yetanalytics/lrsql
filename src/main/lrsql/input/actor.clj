(ns lrsql.input.actor
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.spec.common :as c]
            [lrsql.spec.actor :as as]
            [lrsql.util :as u]
            [lrsql.util.actor :as au]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-actor-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::as/actor-input))

(defn insert-actor-input
  "Given the xAPI `actor`, construct an entry for the `:actor-inputs` vec in
   the `insert-statement!` input param map, or `nil` if `actor` does not have
   an IFI."
  [actor]
  (when-some [ifi-str (au/actor->ifi actor)]
    {:table       :actor
     :primary-key (u/generate-squuid)
     :actor-ifi   ifi-str
     :actor-type  (get actor "objectType" "Agent")
     :payload     actor}))

(s/fdef insert-group-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::as/actor-inputs))

(defn insert-group-input
  "Given the xAPI `actor`, return a coll of `:actor-inputs` entries for its
   member actors, or nil if `actor` is not a Group or has no members. Both
   Anonymous and Identified Group members count."
  [actor]
  ;; Use let-binding in order to avoid cluttering args list
  (let [{obj-type "objectType" members  "member"} actor]
    (when (and (= "Group" obj-type) (not-empty members))
      (map insert-actor-input members))))

(s/fdef insert-statement-to-actor-input
  :args (s/cat :statement-id ::c/statement-id
               :actor-usage ::as/usage
               :actor-input ::as/actor-input)
  :ret ::as/stmt-actor-input)

(defn insert-statement-to-actor-input
  "Given `statement-id`, `actor-usage` (e.g. \"Actor\") and the return value
   of `actor-insert-input`, return the input param map for
   `f/insert-statement-to-actor!`."
  [statement-id actor-usage {:keys [actor-ifi actor-type]}]
  {:table        :statement-to-actor
   :primary-key  (u/generate-squuid)
   :statement-id statement-id
   :usage        actor-usage
   :actor-ifi    actor-ifi
   :actor-type   actor-type})



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor Delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(s/fdef delete-actor-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::as/actor-input))
;args/spec: who knows
(defn delete-actor-input [actor-ifi]
;needs changing, should probably be just ifi?
  {:actor-ifi actor-ifi
   :del-statement-ids (query-statement-ids-by-actor tx input)})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef query-agent-input
  :args (s/cat :params ::lrsp/get-person-params)
  :ret as/query-agent-spec)

(defn query-agent-input
  "Given agent query params, create the input param map for `query-agent`."
  [{agent :agent}]
  {:actor-ifi  (au/actor->ifi agent)
   :actor-type "Agent"}) ; Cannot query Groups
