(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.backend.protocol :as ip]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.actor :refer [actor-backend?]]
            [lrsql.spec.activity :refer [activity-backend?]]
            [lrsql.spec.attachment :refer [attachment-backend?]]
            [lrsql.spec.statement :as ss :refer [statement-backend?]]
            [lrsql.util :as u]
            [lrsql.util.activity :as au]
            [lrsql.util.statement :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement!*
  [bk tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input]
    (if-some [{old-stmt :payload}
              (ip/-query-statement bk tx {:statement-id stmt-id})]
      ;; Return nil if the statements aren't actually equal
      (when-not (su/statement-equal? old-stmt new-stmt)
        (throw
         (ex-info "Statement Conflict!"
                  {:type :com.yetanalytics.lrs.protocol/statement-conflict
                   :extant-statement old-stmt
                   :statement        new-stmt})))
      (do
        (ip/-insert-statement! bk tx input)
        (when (:voiding? input)
          (ip/-void-statement! bk tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor!
  [bk tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (ip/-query-actor bk tx)
                               :payload)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (ip/-update-actor! bk tx input)))
    (ip/-insert-actor! bk tx input)))

(defn- insert-activity!
  [bk tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (ip/-query-activity bk tx)
                               :payload)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (au/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (ip/-update-activity! bk tx input'))))
    (ip/-insert-activity! bk tx input)))

(defn- insert-attachment!
  [bk tx input]
  (ip/-insert-attachment! bk tx input))

(defn- insert-stmt-actor!
  [bk tx input]
  (ip/-insert-statement-to-actor! bk tx input))

(defn- insert-stmt-activity!
  [bk tx input]
  (ip/-insert-statement-to-activity! bk tx input))

(defn- insert-stmt-stmt!
  [bk tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (ip/-query-statement-exists bk tx input')]
    (when exists (ip/-insert-statement-to-statement! bk tx input))))

(s/fdef insert-statement!
  :args (s/cat :bk (s/and statement-backend?
                           actor-backend?
                           activity-backend?
                           attachment-backend?)
               :tx transaction?
               :inputs ss/insert-statement-input-spec)
  :ret ::lrsp/store-statements-ret)

(defn insert-statement!
  "Insert the statement and auxillary objects and attachments that are given
   by `input`. Returns a map with the property `:statement-ids` on success,
   or one with the `:error` property on failure."
  [bk tx {:keys [statement-input
                  actor-inputs
                  activity-inputs
                  attachment-inputs
                  stmt-actor-inputs
                  stmt-activity-inputs
                  stmt-stmt-inputs]
           :as input}]
  (let [?stmt-id (insert-statement!* bk tx statement-input)]
    (dorun (map (partial insert-actor! bk tx) actor-inputs))
    (dorun (map (partial insert-activity! bk tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor! bk tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity! bk tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt! bk tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment! bk tx) attachment-inputs))
    ;; Return the statement ID on success, error on failure
    (if ?stmt-id
      {:statement-ids [(u/uuid->str ?stmt-id)]}
      {:error (ex-info "Could not insert statement."
                       {:type  ::statement-insertion-error
                        :input input})})))
