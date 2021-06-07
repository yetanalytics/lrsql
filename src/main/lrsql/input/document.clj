(ns lrsql.input.document
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.xapi.document]
            [lrsql.util  :as u]
            [lrsql.util.actor :as ua]
            [lrsql.util.document :as ud]
            [lrsql.spec.document :as ds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Basics 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- state-document-basics
  "Common properties for state document inputs. `state-id?` controls whether
   the state ID property is added (true for singleton queries, false for
   array-valued queries). `add-registration?` controls whether a registration
   is added even if it is not present (in which case `nil` is assoc'd)."
  [{?state-id     :stateId
    activity-id   :activityId
    agent         :agent
    ?registration :registration}
   state-id?
   add-registration?]
  (cond-> {:table         :state-document
           :activity-iri  activity-id
           :agent-ifi     (ua/actor->ifi agent)}
    state-id?
    (assoc :state-id ?state-id)
    (or add-registration? ?registration)
    (assoc :registration (when ?registration (u/str->uuid ?registration)))))

(defn- agent-profile-document-basics
  "Common properties for agent profile document inputs. `profile-id?` controls
   whether the profile ID property is added (true for singleton queries, false
   for array-valued queries)."
  [{?profile-id :profileId
    agent       :agent}
   profile-id?]
  (cond-> {:table     :agent-profile-document
           :agent-ifi (ua/actor->ifi agent)}
    profile-id?
    (assoc :profile-id ?profile-id)))

(defn- activity-profile-document-basics
  "Common properties for activity profile document inputs. `profile-id?`
   controls whether the profile ID property is added (true for singleton
   queries, false for array-valued queries)."
  [{?profile-id :profileId
    activity-id :activityId}
   profile-id?]
  (cond-> {:table        :activity-profile-document
           :activity-iri activity-id}
    profile-id?
    (assoc :profile-id ?profile-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- document-insert-basics
  "Common properties for insertion inputs, including the primary key, the last
   modified time, and `document`"
  [document]
  (let [{squuid    :squuid
         squuid-ts :timestamp} (u/generate-squuid*)]
    (merge {:primary-key   squuid
            :last-modified squuid-ts}
           (-> document
               (select-keys [:content-type :content-length :contents])
               (update :contents u/data->bytes)))))

(s/fdef document-insert-input
  :args (s/cat :params ds/set-document-params
               :document :com.yetanalytics.lrs.xapi/document)
  :ret ds/document-insert-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-insert-input
  "Given `params` and `document`, construct the input for `insert-document!`
   and `update-document!`"
  {:arglists '([params document])}
  (fn [params _] (ud/document-dispatch params)))

(defmethod document-insert-input :state-document
  [params document]
  (merge (state-document-basics params true true)
         (document-insert-basics document)))

(defmethod document-insert-input :agent-profile-document
  [params document]
  (merge (agent-profile-document-basics params true)
         (document-insert-basics document)))

(defmethod document-insert-input :activity-profile-document
  [params document]
  (merge (activity-profile-document-basics params true)
         (document-insert-basics document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Query + Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Single document query/deletion

(s/fdef document-input
  :args (s/cat :params ds/get-or-delete-document-params)
  :ret ds/document-input-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-input
  "Given `params`, construct the input for `query-document` and
   `delete-document!`"
  {:arglists '([params])}
  ud/document-dispatch)

(defmethod document-input :state-document
  [params]
  (state-document-basics params true false))

(defmethod document-input :agent-profile-document
  [params]
  (agent-profile-document-basics params true))

(defmethod document-input :activity-profile-document
  [params]
  (activity-profile-document-basics params true))

;; Multiple document deletion
;; Multi-delete is only supported for state docs, thus no need for multimethod

(s/fdef document-multi-input
  :args (s/cat :params ds/delete-documents-params)
  :ret ds/state-doc-multi-input-spec)

(defn document-multi-input
  "Given params, construct the input for `delete-documents!`"
  [params]
  (assert (and (:activityId params) (:agent params))) ; for testing
  (state-document-basics params false false))

;; Multiple document ID query

(defn- add-since-to-map
  "Add the `:since` property to `m` if `:since` is present/not nil."
  [{?since :since} m]
  (cond-> m
    ?since (assoc :since (u/str->time ?since))))

(s/fdef document-ids-input
  :args (s/cat :params ds/get-document-ids-params)
  :ret ds/document-ids-query-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-ids-input
  "Given `params`, return the input for `query-document-ids`."
  {:arglist '([params])}
  ud/document-dispatch)

(defmethod document-ids-input :state-document
  [params]
  (->> (state-document-basics params false false)
       (add-since-to-map params)))

(defmethod document-ids-input :agent-profile-document
  [params]
  (->> (agent-profile-document-basics params false)
       (add-since-to-map params)))

(defmethod document-ids-input :activity-profile-document
  [params]
  (->> (activity-profile-document-basics params false)
       (add-since-to-map params)))
