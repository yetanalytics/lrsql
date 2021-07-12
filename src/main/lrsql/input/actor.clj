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

(s/fdef actor-insert-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::as/actor-input))

(defn actor-insert-input
  "Given `actor`, construct the input for `functions/insert-actor!`, or nil
   if it does not have an IFI."
  [actor]
  (when-some [ifi-str (au/actor->ifi actor)]
    {:table       :actor
     :primary-key (u/generate-squuid)
     :actor-ifi   ifi-str
     :actor-type  (get actor "objectType" "Agent")
     :payload     actor}))

(s/fdef group-insert-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::as/actor-inputs))

(defn group-insert-input
  "Given `actor`, return a coll of actor inputs, or nil if `actor` is not
   a Group or has no members. Both Anonymous and Identified Group members
   count."
  [actor]
  ;; Use let-binding in order to avoid cluttering args list
  (let [{obj-type "objectType" members  "member"} actor]
    (when (and (= "Group" obj-type) (not-empty members))
      (map actor-insert-input members))))

(s/fdef statement-to-actor-insert-input
  :args (s/cat :statement-id ::c/statement-id
               :actor-usage ::as/usage
               :actor-input ::as/actor-input)
  :ret ::as/stmt-actor-input)

(defn statement-to-actor-insert-input
  "Given `statement-id`, `actor-usage` and the return value of
   `actor-insert-input`, return the input for
   `functions/insert-statement-to-actor!`."
  [statement-id actor-usage {:keys [actor-ifi actor-type]}]
  {:table        :statement-to-actor
   :primary-key  (u/generate-squuid)
   :statement-id statement-id
   :usage        actor-usage
   :actor-ifi    actor-ifi
   :actor-type   actor-type})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef agent-query-input
  :args (s/cat :params ::lrsp/get-person-params)
  :ret as/query-agent-spec)

(defn agent-query-input
  "Construct an input for `query-agent!`"
  [{agent :agent}]
  {:actor-ifi  (au/actor->ifi agent)
   :actor-type "Agent"}) ; Cannot query Groups
