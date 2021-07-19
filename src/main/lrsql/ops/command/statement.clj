(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c :refer [transaction?]]
            [lrsql.spec.statement :as ss]
            [lrsql.util :as u]
            [lrsql.util.activity :as au]
            [lrsql.util.statement :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement!*
  [interface tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input
        exists (ip/-query-statement-exists interface
                                           tx
                                           {:statement-id stmt-id})]
    (if exists
      (let [{old-stmt :payload}
            (or (ip/-query-statement interface tx {:statement-id stmt-id
                                                   :voided?      false})
                (ip/-query-statement interface tx {:statement-id stmt-id
                                                   :voided?      true}))]
        ;; Return nil if the statements aren't actually equal
        (when-not (su/statement-equal? old-stmt new-stmt)
          (throw
           (ex-info "Statement Conflict!"
                    {:type :com.yetanalytics.lrs.protocol/statement-conflict
                     :extant-statement old-stmt
                     :statement        new-stmt}))))
      (do
        (ip/-insert-statement! interface tx input)
        (when (:voiding? input)
          (ip/-void-statement! interface tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor!
  [interface tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (ip/-query-actor interface tx)
                               :payload)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (ip/-update-actor! interface tx input)))
    (ip/-insert-actor! interface tx input)))

(defn- insert-activity!
  [interface tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (ip/-query-activity interface tx)
                               :payload)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (au/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (ip/-update-activity! interface tx input'))))
    (ip/-insert-activity! interface tx input)))

(defn- insert-attachment!
  [interface tx input]
  (ip/-insert-attachment! interface tx input))

(defn- insert-stmt-actor!
  [interface tx input]
  (ip/-insert-statement-to-actor! interface tx input))

(defn- insert-stmt-activity!
  [interface tx input]
  (ip/-insert-statement-to-activity! interface tx input))

(defn- insert-stmt-stmt!
  [interface tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (ip/-query-statement-exists interface tx input')]
    (when exists (ip/-insert-statement-to-statement! interface tx input))))

(s/fdef insert-statement!
  :args (s/cat :interface c/insert-interface?
               :tx transaction?
               :inputs ss/insert-statement-input-spec)
  :ret ::lrsp/store-statements-ret)

(defn insert-statement!
  "Insert the statement and auxillary objects and attachments that are given
   by `input`. Returns a map with the property `:statement-ids` on success,
   or one with the `:error` property on failure."
  [interface
   tx
   {:keys [statement-input
           actor-inputs
           activity-inputs
           attachment-inputs
           stmt-actor-inputs
           stmt-activity-inputs
           stmt-stmt-inputs]
    :as input}]
  (let [?stmt-id (insert-statement!* interface tx statement-input)]
    (dorun (map (partial insert-actor! interface tx) actor-inputs))
    (dorun (map (partial insert-activity! interface tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor! interface tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity! interface tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt! interface tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment! interface tx) attachment-inputs))
    ;; Return the statement ID on success, error on failure
    (if ?stmt-id
      {:statement-ids [(u/uuid->str ?stmt-id)]}
      {:error (ex-info "Could not insert statement."
                       {:type  ::statement-insertion-error
                        :input input})})))
