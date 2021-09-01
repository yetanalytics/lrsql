(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.actor :refer [actor-backend?]]
            [lrsql.spec.activity :refer [activity-backend?]]
            [lrsql.spec.attachment :refer [attachment-backend?]]
            [lrsql.spec.statement :as ss :refer [statement-backend?]]
            [lrsql.util :as u]
            [lrsql.util.activity :as au]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- insert-statement!*
  [bk tx input]
  (let [{stmt-id  :statement-id
         sref-id  :statement-ref-id}
        input]
    (bp/-insert-statement! bk tx input)
    (when (:voiding? input)
      (bp/-void-statement! bk tx {:statement-id sref-id}))
    stmt-id))

(defn- insert-actor!
  [bk tx input]
  (if-some [old-actor (some->> (select-keys input [:actor-ifi
                                                   :actor-type])
                               (bp/-query-actor bk tx)
                               :payload)]
    (let [{new-actor :payload} input
          {old-name "name"}    old-actor
          {new-name "name"}    new-actor]
      (when-not (= old-name new-name)
        (bp/-update-actor! bk tx input)))
    (bp/-insert-actor! bk tx input)))

(defn- insert-activity!
  [bk tx input]
  (if-some [old-activ (some->> (select-keys input [:activity-iri])
                               (bp/-query-activity bk tx)
                               :payload)]
    (let [{new-activ :payload} input]
      (when-not (= old-activ new-activ)
        (let [activity' (au/merge-activities old-activ new-activ)
              input'    (assoc input :payload activity')]
          (bp/-update-activity! bk tx input'))))
    (bp/-insert-activity! bk tx input)))

(defn- insert-attachment!
  [bk tx input]
  (bp/-insert-attachment! bk tx input))

(defn- insert-stmt-actor!
  [bk tx input]
  (bp/-insert-statement-to-actor! bk tx input))

(defn- insert-stmt-activity!
  [bk tx input]
  (bp/-insert-statement-to-activity! bk tx input))

(defn- insert-stmt-stmt!
  [bk tx input]
  (let [input' {:statement-id (:descendant-id input)}
        exists (bp/-query-statement-exists bk tx input')]
    (when exists (bp/-insert-statement-to-statement! bk tx input))))

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
   by `input`. Returns a map of a singleton vector containing the statement ID."
  [bk tx {:keys [statement-input
                  actor-inputs
                  activity-inputs
                  attachment-inputs
                  stmt-actor-inputs
                  stmt-activity-inputs
                  stmt-stmt-inputs]}]
  (let [stmt-id (insert-statement!* bk tx statement-input)]
    (dorun (map (partial insert-actor! bk tx) actor-inputs))
    (dorun (map (partial insert-activity! bk tx) activity-inputs))
    (dorun (map (partial insert-stmt-actor! bk tx) stmt-actor-inputs))
    (dorun (map (partial insert-stmt-activity! bk tx) stmt-activity-inputs))
    (dorun (map (partial insert-stmt-stmt! bk tx) stmt-stmt-inputs))
    (dorun (map (partial insert-attachment! bk tx) attachment-inputs))
    {:statement-ids [(u/uuid->str stmt-id)]}))
