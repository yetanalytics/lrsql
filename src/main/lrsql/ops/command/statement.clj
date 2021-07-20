(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.actor :refer [actor-interface?]]
            [lrsql.spec.activity :refer [activity-interface?]]
            [lrsql.spec.attachment :refer [attachment-interface?]]
            [lrsql.spec.statement :as ss :refer [statement-interface?]]
            [lrsql.util :as u]
            [lrsql.util.activity :as au]
            [lrsql.util.statement :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement!*
  [inf tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input]
    (if-some [{old-stmt :payload}
              (ip/-query-statement inf tx {:statement-id stmt-id})]
      ;; Return nil if the statements aren't actually equal
      (when-not (su/statement-equal? old-stmt new-stmt)
        (throw
         (ex-info "Statement Conflict!"
                  {:type :com.yetanalytics.lrs.protocol/statement-conflict
                   :extant-statement old-stmt
                   :statement        new-stmt})))
      (do
        (ip/-insert-statement! inf tx input)
        (when (:voiding? input)
          (ip/-void-statement! inf tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor!
  [inf tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (ip/-query-actor inf tx)
                               :payload)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (ip/-update-actor! inf tx input)))
    (ip/-insert-actor! inf tx input)))

(defn- insert-activity!
  [inf tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (ip/-query-activity inf tx)
                               :payload)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (au/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (ip/-update-activity! inf tx input'))))
    (ip/-insert-activity! inf tx input)))

(defn- insert-attachment!
  [inf tx input]
  (ip/-insert-attachment! inf tx input))

(defn- insert-stmt-actor!
  [inf tx input]
  (ip/-insert-statement-to-actor! inf tx input))

(defn- insert-stmt-activity!
  [inf tx input]
  (ip/-insert-statement-to-activity! inf tx input))

(defn- insert-stmt-stmt!
  [inf tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (ip/-query-statement-exists inf tx input')]
    (when exists (ip/-insert-statement-to-statement! inf tx input))))

(s/fdef insert-statement!
  :args (s/cat :inf (s/and statement-interface?
                           actor-interface?
                           activity-interface?
                           attachment-interface?)
               :tx transaction?
               :inputs ss/insert-statement-input-spec)
  :ret ::lrsp/store-statements-ret)

(defn insert-statement!
  "Insert the statement and auxillary objects and attachments that are given
   by `input`. Returns a map with the property `:statement-ids` on success,
   or one with the `:error` property on failure."
  [inf tx {:keys [statement-input
                  actor-inputs
                  activity-inputs
                  attachment-inputs
                  stmt-actor-inputs
                  stmt-activity-inputs
                  stmt-stmt-inputs]
           :as input}]
  (let [?stmt-id (insert-statement!* inf tx statement-input)]
    (dorun (map (partial insert-actor! inf tx) actor-inputs))
    (dorun (map (partial insert-activity! inf tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor! inf tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity! inf tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt! inf tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment! inf tx) attachment-inputs))
    ;; Return the statement ID on success, error on failure
    (if ?stmt-id
      {:statement-ids [(u/uuid->str ?stmt-id)]}
      {:error (ex-info "Could not insert statement."
                       {:type  ::statement-insertion-error
                        :input input})})))

(when (some? (:voided? {:statement-id 2})) "AND is_voided = :voided?")