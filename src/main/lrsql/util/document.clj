(ns lrsql.util.document
  (:require [com.yetanalytics.lrs.util.hash :as hash]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.backend.protocol :as bp]
            [lrsql.util :as u]))

(defn canonical-state-document-ids
  "Return State document IDs in the canonical order used by collection GET
   responses and collection precondition validation."
  [ids]
  (->> ids sort vec))

(defn state-document-ids-contents
  "Canonicalize and serialize State document IDs exactly as the LRS document
   route serializes its JSON response body."
  [ids]
  (u/write-json (canonical-state-document-ids ids)))

(defn state-document-ids-etag
  "Return the unquoted SHA-1 ETag for a canonical State document ID vector."
  [ids]
  (hash/sha-1 (state-document-ids-contents ids)))

(defn preconditions
  "Return the normalized ETag preconditions supplied by the LRS library.
   An absent key represents a request without preconditions."
  [ctx]
  (get ctx ::lrs-doc/preconditions {}))

(defn current-document-state
  "Return the existence and unquoted ETag state for an observed database
   document. The ETag is the SHA-1 hash of the exact stored contents."
  [document]
  (if-some [contents (:contents document)]
    {:exists? true
     :etag    (hash/sha-1 contents)}
    {:exists? false}))

(defn preconditions-met?
  "Return true when the normalized context preconditions are met by the
   observed database document."
  [ctx document]
  (lrs-doc/etag-preconditions-met? (preconditions ctx)
                                   (current-document-state document)))

(defn precondition-error
  "Return the standard LRS document precondition error when the normalized
   context preconditions fail for the observed document, otherwise nil."
  ([ctx document]
   (precondition-error ctx document {}))
  ([ctx document data]
   (when-not (preconditions-met? ctx document)
     (lrs-doc/precondition-failed-error data))))

(defn cas-conflict-ex
  "Create an internal exception indicating that a document CAS operation did
   not apply and its transaction must be retried with a fresh snapshot."
  ([]
   (cas-conflict-ex {}))
  ([data]
   (ex-info "Document compare-and-swap conflict"
            (assoc data :type ::cas-conflict))))

(defn cas-conflict?
  "Return true when `ex` represents an internal document CAS conflict."
  [ex]
  (= ::cas-conflict (:type (ex-data ex))))

(defn throw-cas-conflict!
  "Throw an internal document CAS conflict exception."
  ([]
   (throw-cas-conflict! {}))
  ([data]
   (throw (cas-conflict-ex data))))

(defn document-txn-retry?
  "Return true when a document transaction should be retried, either because
   its CAS predicate missed or the backend recognizes the database error."
  [backend ex]
  (or (cas-conflict? ex)
      (bp/-txn-retry? backend ex)))

(defn document-retry-opts
  "Build document transaction retry options from the existing statement retry
   limit and budget configuration."
  [backend config]
  {:retry-test  (partial document-txn-retry? backend)
   :max-attempt (:stmt-retry-limit config)
   :budget      (:stmt-retry-budget config)})

(defn document-dispatch
  "Return either `:state-document`, `:agent-profile-document`, or
   `:activity-profile-document` depending on the fields in `params`. Works for
   both ID params and query params."
  [{?state-id    :stateId
    ?profile-id  :profileId
    ?activity-id :activityId
    ?agent       :agent
    :as          params}]
  (cond
    ;; ID params
    ?state-id
    :state-document
    (and ?profile-id ?agent)
    :agent-profile-document
    (and ?profile-id ?activity-id)
    :activity-profile-document
    ;; Query params
    (and ?activity-id ?agent)
    :state-document
    ?activity-id
    :activity-profile-document
    ?agent
    :agent-profile-document
    ;; Error
    :else
    (throw (ex-info "Invalid document ID or query parameters!"
                    {:type   ::invalid-document-resource-params
                     :params params}))))
