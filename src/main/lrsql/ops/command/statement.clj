(ns lrsql.ops.command.statement
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [xapi-schema.spec :as xs]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as cs :refer [transaction?]]
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

(defn- check-stmt-conflict
  "Check if the statement already exists in the DB and return a map if it does.
   The `statement-equal?` prop determines if the two statements are equal
   (according to Statement Immutability guidelines)."
  [bk tx input]
  (let [{stmt-id  :statement-id
         new-stmt :payload} input]
    ;; Optimization: if the statement ID is auto-assigned by lrsql, then
    ;; we can assume that no collisions are possible (given the extreme
    ;; rarity of random UUID collisions).
    (when-not (contains? (-> new-stmt meta :assigned-vals) :id)
      (when-some [{old-stmt :payload} (bp/-query-statement
                                       bk
                                       tx
                                       {:statement-id stmt-id})]
        ;; Conflict! Return enough info to create an ex-info map if needed.
        {:extant-statement old-stmt
         :statement        new-stmt
         :statement-equal? (su/statement-equal? old-stmt new-stmt)}))))

(defn- insert-statement!*
  "Tries to insert a statement. There are three possible results:
   - Success: returns the statement (UU)ID
   - Equal statement exists: returns `nil`
   - Not equal statement exists: return a map containing an `:error`."
  [bk tx input]
  (let [{stmt-id :statement-id
         sref-id :statement-ref-id} input]
    (if-some [err (check-stmt-conflict bk tx input)]
      ;; Conflict! return error or nil
      (when-not (:statement-equal? err)
        {:error (ex-info (format "Statement Conflict on ID %s" stmt-id)
                         (-> err
                             (dissoc :statement-equal?)
                             (assoc :type ::lrsp/statement-conflict)))})
      ;; No conflict! insert statement (and void if needed)
      (do
        (bp/-insert-statement! bk tx input)
        (when (:voiding? input)
          (bp/-void-statement! bk tx {:statement-id sref-id}))
        stmt-id))))

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
    ;; add objectType for comparison in order to avoid unnecessary writes
    (let [new-activ (assoc (:payload input) "objectType" "Activity")]
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

;; Need separate statement-id spec since we return string UUIDs, not
;; java.util.UUID instances
(s/def ::statement-id ::xs/uuid)

(s/fdef insert-statement!
  :args (s/cat :bk (s/and statement-backend?
                          actor-backend?
                          activity-backend?
                          attachment-backend?)
               :tx transaction?
               :inputs ss/insert-statement-input-spec)
  :ret (s/or :success             (s/keys :req-un [::statement-id])
             :conflict-equals     (fn [m] (not (contains? m :statement-id)))
             :conflict-not-equals (s/keys :req-un [::cs/error])))

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
  (let [stmt-res (insert-statement!* bk tx statement-input)]
    (cond
      ;; Statement inserted; insert everything else
      (uuid? stmt-res)
      (do
        (run! (partial insert-actor! bk tx) actor-inputs)
        (run! (partial insert-activity! bk tx) activity-inputs)
        (run! (partial insert-stmt-actor! bk tx) stmt-actor-inputs)
        (run! (partial insert-stmt-activity! bk tx) stmt-activity-inputs)
        (run! (partial insert-stmt-stmt! bk tx) stmt-stmt-inputs)
        (run! (partial insert-attachment! bk tx) attachment-inputs)
        {:statement-id (u/uuid->str stmt-res)})

      ;; Equal statement exists; return nothing
      (nil? stmt-res)
      {}

      ;; Statement conflict; return map containing `:error`
      (contains? stmt-res :error)
      stmt-res

      ;; What the pineapple? Trigger a 500 Internal Server Error.
      :else
      {:error (ex-info "Unexpected error while inserting statement."
                       {:type   ::statement-insertion-error
                        :input  input
                        :result stmt-res})})))
