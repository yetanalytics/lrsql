(ns lrsql.spec.document
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.xapi.document :as lrs-doc]
            [lrsql.spec.common    :as c]
            [lrsql.spec.activity  :as hs-activ]
            [lrsql.spec.actor     :as hs-actor]
            [lrsql.spec.statement :as hs-stmt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Table
;; Also found in other input maps, but these are only necessary for documents
(s/def :lrsql.spec.document.state/table
  #{:state-document})
(s/def :lrsql.spec.document.agent-profile/table
  #{:agent-profile-document})
(s/def :lrsql.spec.document.activity-profile/table
  #{:activity-profile-document})

;; Parameters
;; NOTE: Profile ID should be IRI, but xapi-schema defines it only as a string
(s/def ::state-id string?)
(s/def ::profile-id string?)
(s/def ::last-modified c/instant-spec)
(s/def ::contents bytes?)

;; Query-specific params
(s/def ::since c/instant-spec)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params specs
;; These spec the data received by functions in `lrsql.hugsq.input`.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Need to define new doc specs here in order to work with s/fdef.

(def set-document-params
  (s/and ::lrsp/set-document-params (s/conformer second)))

(def get-or-delete-document-params
  (s/and ::lrsp/get-document-params (s/conformer second)))

(def delete-documents-params
  (s/and ::lrsp/delete-documents-params (s/conformer second)))

(def get-document-ids-params
  (s/and ::lrsp/get-document-ids-params (s/conformer second)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion (+ Upsertion) spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State-Document
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - state_id:       STRING NOT NULL
;; - activity_iri:   STRING NOT NULL
;; - agent_ifi:      STRING NOT NULL
;; - registration:   UUID
;; - last_modified:  TIMESTAMP NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - contents:       BINARY NOT NULL

(def insert-state-doc-input-spec
  (s/keys :req-un [::c/primary-key
                   :lrsql.spec.document.state/table
                   ::state-id
                   ::hs-activ/activity-iri
                   ::hs-actor/agent-ifi
                   ::hs-stmt/registration ; nilable
                   ::last-modified
                   ::lrs-doc/content-type
                   ::lrs-doc/content-length
                   ::contents]))

;; Agent-Profile-Document
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - profile_id:     STRING NOT NULL
;; - agent_ifi:      STRING NOT NULL
;; - last_modified:  TIMESTAMP NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - contents:       BINARY NOT NULL

(def insert-agent-profile-doc-input-spec
  (s/keys :req-un [::c/primary-key
                   :lrsql.spec.document.agent-profile/table
                   ::profile-id
                   ::hs-actor/agent-ifi
                   ::last-modified
                   ::lrs-doc/content-type
                   ::lrs-doc/content-length
                   ::contents]))

;; Activity-Profile-Resource
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - profile_id:     STRING NOT NULL
;; - activity_iri:   STRING NOT NULL
;; - last_modified:  TIMESTAMP NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - contents:       BINARY NOT NULL

(def insert-activity-profile-doc-spec
  (s/keys :req-un [::c/primary-key
                   :lrsql.spec.document.activity-profile/table
                   ::profile-id
                   ::hs-activ/activity-iri
                   ::last-modified
                   ::lrs-doc/content-type
                   ::lrs-doc/content-length
                   ::contents]))

;; Putting it all together
;; NOTE: need to call s/nonconforming to make it work with s/fdef's :fn

(def insert-document-spec
  (s/nonconforming
   (s/or :state insert-state-doc-input-spec
         :agent-profile insert-agent-profile-doc-input-spec
         :activity-profile insert-activity-profile-doc-spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Queries + Deletions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Document queries/deletions

(def state-doc-input-spec
  (s/keys :req-un [::state-id
                   :lrsql.spec.document.state/table
                   ::hs-activ/activity-iri
                   ::hs-actor/agent-ifi]
          :opt-un [::hs-stmt/registration]))

(def agent-profile-doc-input-spec
  (s/keys :req-un [::hs-actor/agent-ifi
                   :lrsql.spec.document.agent-profile/table
                   ::profile-id]))

(def activity-profile-doc-input-spec
  (s/keys :req-un [::hs-activ/activity-iri
                   :lrsql.spec.document.activity-profile/table
                   ::profile-id]))

(def document-input-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-input-spec
         :agent-profile agent-profile-doc-input-spec
         :activity-profile activity-profile-doc-input-spec)))

;; Document multi-query/delete

(def state-doc-multi-input-spec
  (s/keys :req-un [:lrsql.spec.document.state/table
                   ::hs-activ/activity-iri
                   ::hs-actor/agent-ifi]
          :opt-un [::hs-stmt/registration]))

;; Document ID queries

(def state-doc-ids-input-spec
  (s/keys :req-un [:lrsql.spec.document.state/table
                   ::hs-activ/activity-iri
                   ::hs-actor/agent-ifi]
          :opt-un [::hs-stmt/registration
                   ::since]))

(def agent-profile-doc-ids-input-spec
  (s/keys :req-un [:lrsql.spec.document.agent-profile/table
                   ::hs-actor/agent-ifi]
          :opt-un [::since]))

(def activity-profile-doc-ids-input-spec
  (s/keys :req-un [:lrsql.spec.document.activity-profile/table
                   ::hs-activ/activity-iri]
          :opt-un [::since]))

(def document-ids-query-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-ids-input-spec
         :agent-profile agent-profile-doc-ids-input-spec
         :activity-profile activity-profile-doc-ids-input-spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Result Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def document-command-res-spec
  "Alias for `(lrsp/or-error #{{}})`, which is the common spec shared
   for all document commands."
  (lrsp/or-error #{{}}))
