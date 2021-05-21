(ns lrsql.hugsql.input.util
  (:require [lrsql.hugsql.util :as u]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Actors and Groups are encoded without any intention of being parsed
;; back, hence this sort of simple encoding is sufficient.
(defn actor->ifi
  "Returns string of the format \"<ifi-type>::<ifi-value>\".
   Returns `nil` if `actor` doesn't have an IFI (e.g. Anonymous Group)."
  [actor]
  (let [{mbox    "mbox"
         sha     "mbox_sha1sum"
         openid  "openid"
         account "account"}
        actor]
    (cond
      mbox    (str "mbox::" mbox)
      sha     (str "mbox_sha1sum::" sha)
      openid  (str "openid::" openid)
      account (let [{acc-name "name"
                     acc-page "homePage"}
                    account]
                (str "account::" acc-name "@" acc-page))
      :else   nil)))

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
      (assoc "stored" (u/time->str (u/current-time)))
      (not id)
      (assoc "id" (u/uuid->str (u/generate-uuid)))
      (not timestamp)
      (assoc "timestamp" (u/time->str (u/current-time)))
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
