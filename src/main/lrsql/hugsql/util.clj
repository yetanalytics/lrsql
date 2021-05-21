(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-invalid-str-ex
  "Wrap `(parse-fn s)` in an exception such that on parse failure, the
   folloiwing error data is thrown:
     :kind      ::invalid-string
     :string    `s`
     :str-type  `str-type`"
  [str-type parse-fn s]
  `(try (~parse-fn ~s)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse nil or invalid %s string"
                                  ~str-type)
                          {:kind     ::invalid-string
                           :string   ~s
                           :str-type ~str-type})))))

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
  (with-invalid-str-ex "UUID" java.util.UUID/fromString uuid-str))

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
  (with-invalid-str-ex "timestamp" jt/instant ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp into a string."
  [ts]
  (jt/format ts))

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
