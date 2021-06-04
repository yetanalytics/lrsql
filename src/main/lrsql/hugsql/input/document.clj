(ns lrsql.hugsql.input.document
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.xapi.document]
            [lrsql.hugsql.util  :as u]
            [lrsql.hugsql.util.actor :as ua]
            [lrsql.hugsql.util.document :as ud]
            [lrsql.hugsql.spec.document :as hs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Basics 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- state-document-basics
  "Common properties for state document inputs. `state-id?` controls whether
   the state ID property is added (true for singleton queries, false for
   array-valued queries)."
  [{?state-id     :stateId
    activity-id   :activityId
    agent         :agent
    ?registration :registration}
   state-id?]
  (cond-> {:table         :state-document
           :activity-iri  activity-id
           :agent-ifi     (ua/actor->ifi agent)
           :?registration (when ?registration (u/str->uuid ?registration))}
    state-id?
    (assoc :state-id ?state-id)))

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
  :args (s/cat :params hs/set-document-params
               :document :com.yetanalytics.lrs.xapi/document)
  :ret hs/document-insert-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-insert-input
  "Given `params` and `document`, construct the input for
   `command/insert-document!` and `command/update-document!`"
  {:arglists '([params document])}
  (fn [params _] (ud/document-dispatch params)))

(defmethod document-insert-input :state-document
  [params document]
  (merge (state-document-basics params true)
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
  :args (s/cat :params hs/get-or-delete-document-params)
  :ret hs/document-input-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-input
  "Given `params`, construct the input for `command/query-document` and
   `command/delete-document!`"
  {:arglists '([params])}
  ud/document-dispatch)

(defmethod document-input :state-document
  [params]
  (state-document-basics params true))

(defmethod document-input :agent-profile-document
  [params]
  (agent-profile-document-basics params true))

(defmethod document-input :activity-profile-document
  [params]
  (activity-profile-document-basics params true))

;; Multiple document deletion
;; Multi-delete is only supported for state docs, thus no need for multimethod

(s/fdef document-multi-input
  :args (s/cat :params hs/delete-documents-params)
  :ret hs/state-doc-multi-input-spec)

(defn document-multi-input
  "Given params, construct the input for `command/delete-document!` in the
   case of multiple documents."
  [params]
  (assert (and (:activityId params) (:agent params))) ; for testing
  (state-document-basics params false))

;; Multiple document ID query

(defn- add-since-to-map
  "Add the `:since` property to `m` if `:since` is present/not nil."
  [{?since :since} m]
  (merge m
         {:?since (when ?since (u/str->time ?since))}))

(s/fdef document-ids-input
  :args (s/cat :params hs/get-document-ids-params)
  :ret hs/document-ids-query-spec
  :fn (fn [{:keys [args ret]}]
        (= (ud/document-dispatch (:params args)) (:table ret))))

(defmulti document-ids-input
  "Given `params`, return the input for `command/query-document-ids`."
  {:arglist '([params])}
  ud/document-dispatch)

(defmethod document-ids-input :state-document
  [params]
  (->> (state-document-basics params false)
       (add-since-to-map params)))

(defmethod document-ids-input :agent-profile-document
  [params]
  (->> (agent-profile-document-basics params false)
       (add-since-to-map params)))

(defmethod document-ids-input :activity-profile-document
  [params]
  (->> (activity-profile-document-basics params false)
       (add-since-to-map params)))
