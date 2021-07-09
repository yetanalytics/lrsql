(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [lrsql.functions :as f]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.statement :as ss]
            [lrsql.util :as u]
            [lrsql.util.activity :as ua]
            [lrsql.util.statement :as us]))

(defn- prepare-input
  "Prepare the input for insertion. In particular, convert the payload into a
   JSON string."
  [input]
  (update input :payload u/write-json))

(defn- insert-statement-input!
  [tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id
         new-stmt :payload} input
        exists (f/query-statement-exists tx {:statement-id stmt-id})]
    (if exists
      (let [old-data (or (f/query-statement tx {:statement-id stmt-id
                                                :voided?      false})
                         (f/query-statement tx {:statement-id stmt-id
                                                :voided?      true}))
            old-stmt (-> old-data :payload u/parse-json)]
        (when-not (us/statement-equal? old-stmt new-stmt)
          (throw
           (ex-info "Statement Conflict!"
                    {:type :com.yetanalytics.lrs.protocol/statement-conflict
                     :extant-statement old-stmt
                     :statement        new-stmt}))))
      (do
        (f/insert-statement! tx (prepare-input input))
        (when (:voiding? input) (f/void-statement! tx {:statement-id sref-id}))
        stmt-id))))

(defn- insert-actor-input!
  [tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (f/query-actor tx)
                               :payload
                               u/parse-json)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (f/update-actor! tx (prepare-input input))))
    (f/insert-actor! tx (prepare-input input))))

(defn- insert-activity-input!
  [tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (f/query-activity tx)
                               :payload
                               u/parse-json)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (ua/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (f/update-activity! tx (prepare-input input')))))
    (f/insert-activity! tx (prepare-input input))))

(defn- insert-attachment-input!
  [tx input]
  (f/insert-attachment! tx input))

(defn- insert-stmt-actor-input!
  [tx input]
  (f/insert-statement-to-actor! tx input))

(defn- insert-stmt-activity-input!
  [tx input]
  (f/insert-statement-to-activity! tx input))

(defn- insert-stmt-stmt-input!
  [tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (f/query-statement-exists tx input')]
    (when exists (f/insert-statement-to-statement! tx input))))

(s/fdef insert-statement!
  :args (s/cat :tx transaction? :inputs ss/statement-insert-map-spec)
  :ret (s/nilable ::ss/statement-id))

(defn insert-statement!
  [tx {:keys [statement-input
              actor-inputs
              activity-inputs
              attachment-inputs
              stmt-actor-inputs
              stmt-activity-inputs
              stmt-stmt-inputs]}]
  (let [?stmt-id (insert-statement-input! tx statement-input)]
    (dorun (map (partial insert-actor-input! tx) actor-inputs))
    (dorun (map (partial insert-activity-input! tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor-input! tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity-input! tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt-input! tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment-input! tx) attachment-inputs))
    ;; Return the statement ID (or nil on failure)
    ?stmt-id))
