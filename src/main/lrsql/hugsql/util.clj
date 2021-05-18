(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-uuid
  "Return a new sequential UUID."
  []
  (clj-uuid/squuid))

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (jt/instant))

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (jt/instant ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp into a string."
  [ts]
  (jt/format ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; If a Statement lacks a version, the version MUST be set to 1.0.0
;; TODO: Change for version 2.0.0
(def xapi-version "1.0.0")

;; TODO: more specific authority
(def lrsql-authority {"name" "LRSQL"
                      "objectType" "Agent"
                      "account" {"homepage" "http://localhost:8080"
                                 "name"     "LRSQL"}})

(defn prepare-statement
  "Prepare `statement` for LRS storage by coll-ifying context activities
   and setting missing id, timestamp, authority, version, and stored
   properties."
  [statement]
  (let [{:strs [id timestamp authority version]} statement]
    ;; first coll-ify context activities
    (cond-> (ss/fix-statement-context-activities statement)
      true ; stored is always set by the LRS
      (assoc "stored" (time->str (current-time)))
      (not id)
      (assoc "id" (uuid->str (generate-uuid)))
      (not timestamp)
      (assoc "timestamp" (time->str (current-time)))
      (not authority)
      (assoc "authority" lrsql-authority)
      (not version)
      (assoc "version" xapi-version))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Documents
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn document-dispatch
  "Return either `:state-document`, `:agent-profile-document`, or
   `:activity-profile-document` depending on the fields in `params`. Works for
   both ID params and query params."
  [{state-id    :stateId
    profile-id  :profileId
    activity-id :activityId
    agent       :agent
    :as         params}]
  (cond
    ;; ID params
    state-id
    :state-document
    (and profile-id agent)
    :agent-profile-document
    (and profile-id activity-id)
    :activity-profile-document
    ;; Query params
    (and activity-id agent)
    :state-document
    activity-id
    :activity-profile-document
    agent
    :agent-profile-document
    ;; Error
    :else
    (throw (ex-info "Invalid document ID or query parameters!"
                    {:kind   ::invalid-document-resource-params
                     :params params}))))
