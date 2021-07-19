(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.functions :as f]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.statement :as ss]
            [lrsql.util :as u]
            [lrsql.util.activity :as au]
            [lrsql.util.statement :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement!*
  [tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input
        exists (f/query-statement-exists tx {:statement-id stmt-id})]
    (if exists
      (let [{old-stmt :payload}
            (or (f/query-statement tx {:statement-id stmt-id
                                       :voided?      false})
                (f/query-statement tx {:statement-id stmt-id
                                       :voided?      true}))]
        ;; Return nil if the statements aren't actually equal
        (when-not (su/statement-equal? old-stmt new-stmt)
          (throw
           (ex-info "Statement Conflict!"
                    {:type :com.yetanalytics.lrs.protocol/statement-conflict
                     :extant-statement old-stmt
                     :statement        new-stmt}))))
      (do
        (f/insert-statement! tx input)
        (when (:voiding? input) (f/void-statement! tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor!
  [tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (f/query-actor tx)
                               :payload)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (f/update-actor! tx input)))
    (f/insert-actor! tx input)))

(defn- insert-activity!
  [tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (f/query-activity tx)
                               :payload)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (au/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (f/update-activity! tx input'))))
    (f/insert-activity! tx input)))

(defn- insert-attachment!
  [tx input]
  (f/insert-attachment! tx input))

(defn- insert-stmt-actor!
  [tx input]
  (f/insert-statement-to-actor! tx input))

(defn- insert-stmt-activity!
  [tx input]
  (f/insert-statement-to-activity! tx input))

(defn- insert-stmt-stmt!
  [tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (f/query-statement-exists tx input')]
    (when exists (f/insert-statement-to-statement! tx input))))

(s/fdef insert-statement!
  :args (s/cat :tx transaction? :inputs ss/insert-statement-input-spec)
  :ret ::lrsp/store-statements-ret)

(defn insert-statement!
  "Insert the statement and auxillary objects and attachments that are given
   by `input`. Returns a map with the property `:statement-ids` on success,
   or one with the `:error` property on failure."
  [tx {:keys [statement-input
              actor-inputs
              activity-inputs
              attachment-inputs
              stmt-actor-inputs
              stmt-activity-inputs
              stmt-stmt-inputs]
       :as input}]
  (let [?stmt-id (insert-statement!* tx statement-input)]
    (dorun (map (partial insert-actor! tx) actor-inputs))
    (dorun (map (partial insert-activity! tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor! tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity! tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt! tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment! tx) attachment-inputs))
    ;; Return the statement ID on success, error on failure
    (if ?stmt-id
      {:statement-ids [(u/uuid->str ?stmt-id)]}
      {:error (ex-info "Could not insert statement."
                       {:type  ::statement-insertion-error
                        :input input})})))
