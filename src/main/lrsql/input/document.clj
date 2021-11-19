(ns lrsql.input.document
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.xapi.document]
            [lrsql.util  :as u]
            [lrsql.util.actor :as au]
            [lrsql.util.document :as du]
            [lrsql.spec.document :as ds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Basics 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- state-document-basics
  "Common properties for state document inputs.
   - `state-id?` controls whether the state ID property is added (true for
   singleton queries, false for array-valued queries).
   - `add-registration?` controls whether a registration is added even if it
   is not present (in which case `nil` is assoc'd)."
  [{?state-id     :stateId
    activity-id   :activityId
    agent         :agent
    ?registration :registration}
   state-id?
   add-registration?]
  (cond-> {:table         :state-document
           :activity-iri  activity-id
           :agent-ifi     (au/actor->ifi agent)}
    state-id?
    (assoc :state-id ?state-id)
    (or add-registration? ?registration)
    (assoc :registration (when ?registration (u/str->uuid ?registration)))))

(defn- agent-profile-document-basics
  "Common properties for agent profile document inputs.
   - `profile-id?` controls whether the profile ID property is added
   (true for singleton queries, false for array-valued queries)."
  [{?profile-id :profileId
    agent       :agent}
   profile-id?]
  (cond-> {:table     :agent-profile-document
           :agent-ifi (au/actor->ifi agent)}
    profile-id?
    (assoc :profile-id ?profile-id)))

(defn- activity-profile-document-basics
  "Common properties for activity profile document inputs.
   - `profile-id?` controls whether the profile ID property is added
   (true for singleton queries, false for array-valued queries)."
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

(defn- insert-document-basics
  "Common properties for insertion inputs, including the primary key, the last
   modified time, and `document`"
  [document]
  (let [{squuid    :squuid
         squuid-ts :timestamp}  (u/generate-squuid*)
        {?ctyp :content-type
         ?clen :content-length
         ctnt* :contents}       document
        ctnt (u/data->bytes ctnt*)
        ctyp (if ?ctyp ?ctyp "application/octet-stream")
        clen (if ?clen ?clen (count ctnt))]
    {:primary-key    squuid
     :last-modified  squuid-ts
     :content-type   ctyp
     :content-length clen
     :contents       ctnt}))

(s/fdef insert-document-input
  :args (s/cat :params ds/set-document-params
               :document :com.yetanalytics.lrs.xapi/document)
  :ret ds/insert-document-spec
  :fn (fn [{:keys [args ret]}]
        (= (du/document-dispatch (:params args)) (:table ret))))

(defmulti insert-document-input
  "Given `params` and `document`, construct the input param map for
   `insert-document!` and `update-document!`"
  {:arglists '([params document])}
  (fn [params _] (du/document-dispatch params)))

(defmethod insert-document-input :state-document
  [params document]
  (merge (state-document-basics params true true)
         (insert-document-basics document)))

(defmethod insert-document-input :agent-profile-document
  [params document]
  (merge (agent-profile-document-basics params true)
         (insert-document-basics document)))

(defmethod insert-document-input :activity-profile-document
  [params document]
  (merge (activity-profile-document-basics params true)
         (insert-document-basics document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Query + Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Single document query/deletion

(s/fdef document-input
  :args (s/cat :params ds/get-or-delete-document-params)
  :ret ds/document-input-spec
  :fn (fn [{:keys [args ret]}]
        (= (du/document-dispatch (:params args)) (:table ret))))

(defmulti document-input
  "Given `params`, construct the input param map for `query-document` and
   `delete-document!`"
  {:arglists '([params])}
  du/document-dispatch)

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
  "Given params, construct the input param map for `delete-documents!`"
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
        (= (du/document-dispatch (:params args)) (:table ret))))

(defmulti document-ids-input
  "Given `params`, construct the input param map for `query-document-ids`."
  {:arglist '([params])}
  du/document-dispatch)

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
