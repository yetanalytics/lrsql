(ns lrsql.hugsql.spec
  "Spec for HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.data.json :as json]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec.activity   :as hs-activ]
            [lrsql.hugsql.spec.actor      :as hs-actor]
            [lrsql.hugsql.spec.attachment :as hs-attach]
            [lrsql.hugsql.spec.util       :refer [make-str-spec]]
            [lrsql.hugsql.util :as u]))

;; TODO: Deal with different encodings for JSON types (e.g. payloads,
;; actor ifi), instead of just H2 strings.

;; TODO
;; Canonical-Language-Maps
;; - id:       SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - iri:      STRING UNIQUE KEY NOT NULL
;; - lang_tag: STRING NOT NULL
;; - value:    STRING NOT NULL

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params specs
;; These spec the data received by functions in `lrsql.hugsq.input`.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-statements-params
  ::lrsp/get-statements-params)

(def get-actor-params
  ::lrsp/get-person-params)

;; For some reason this is not defined in lrs.protocol
(def get-activity-params
  ::lrsp/get-activity-params)

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
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Primary key

(s/def ::primary-key uuid?)

;; Statement IDs

(s/def ::statement-id uuid?)
(s/def ::?statement-ref-id (s/nilable uuid?))

;; Timestamp

(s/def ::timestamp inst?)
(s/def ::stored inst?)

;; Registration

(s/def ::registration uuid?)
(s/def ::?registration (s/nilable uuid?))

;; Verb

(s/def ::verb-iri :verb/id)
(s/def ::voided? boolean?)
(s/def ::voiding? boolean?)

;; Statement

(s/def ::payload
  (make-str-spec ::xs/statement
                 json/read-str
                 json/write-str))

;; Query Options
(s/def ::related-actors? boolean?)
(s/def ::related-activities? boolean?)
(s/def ::since inst?)
(s/def ::until inst?)
(s/def ::limit nat-int?)
(s/def ::ascending? boolean?)

;; Documents
(s/def ::state-id string?)
;; profile ID should be an IRI, but xapi-schema defines it only as a string
(s/def ::profile-id string?)
(s/def ::last-modified inst?)
(s/def ::since inst?)
(s/def ::document bytes?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statements and Attachment Args
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def prepared-statement-spec
  (s/with-gen
    (s/and ::xs/statement
           #(contains? % :statement/id)
           #(contains? % :statement/timestamp)
           #(contains? % :statement/stored)
           #(contains? % :statement/authority))
    #(sgen/fmap u/prepare-statement
                (s/gen ::xs/statement))))

(def statements-attachments-spec
  (s/cat :statements
         (s/coll-of prepared-statement-spec :min-count 1 :gen-max 5)
         :attachments
         (s/coll-of ::ss/attachment :gen-max 2)))

(defn- update-stmt-attachments
  "Update the attachments property of each attachment has an associated
   attachment object in a statement."
  [[statements attachments]]
  (let [num-stmts
        (count statements)
        statements'
        (reduce
         (fn [stmts {:keys [sha2 contentType length] :as _att}]
           (let [n (rand-int num-stmts)]
             (update-in
              stmts
              [n "attachments"]
              (fn [atts]
                (conj atts
                      {"usageType"   "https://example.org/aut"
                       "display"     {"lat" "Lorem Ipsum"}
                       "sha2"        sha2
                       "contentType" contentType
                       "length"      length})))))
         statements
         attachments)]
    [statements' attachments]))

(def prepared-attachments-spec
  (s/with-gen
    statements-attachments-spec
    #(sgen/fmap update-stmt-attachments
                (s/gen statements-attachments-spec))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement
;; - id:               SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id:     UUID NOT NULL UNIQUE KEY
;; - statement_ref_id: UUID
;; - created:          TIMESTAMP NOT NULL -- :timestamp in code
;; - stored:           TIMESTAMP NOT NULL
;; - registration:     UUID
;; - verb_iri:         STRING NOT NULL
;; - is_voided:        BOOLEAN NOT NULL DEFAULT FALSE
;; - payload:          JSON NOT NULL

(def statement-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::?statement-ref-id
                   ::timestamp
                   ::stored
                   ::?registration
                   ::verb-iri
                   ::voided?
                   ::voiding?
                   ::payload]))

;; In this context, "Actor" is a catch-all term to refer to both Agents and
;; Identified Groups, not the Actor object within Statements.

;; Actor
;; - id:          SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - actor_ifi:   STRING NOT NULL UNIQUE KEY
;; - actor_type:  ENUM ('Agent', 'Group') NOT NULL
;; - payload:     JSON NOT NULL

(def actor-insert-spec
  (s/keys :req-un [::primary-key
                   ::hs-actor/actor-ifi
                   ::hs-actor/actor-type
                   ::hs-actor/payload]))

;; Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - activity_iri: STRING NOT NULL UNIQUE KEY
;; - payload:      JSON NOT NULL

(def activity-insert-spec
  (s/keys :req-un [::primary-key
                   ::hs-activ/activity-iri
                   ::hs-activ/payload]))

;; Attachment
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_key:  UUID NOT NULL FOREIGN KEY
;; - attachment_sha: STRING NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - payload:        BINARY NOT NULL

(def attachment-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-attach/attachment-sha
                   ::hs-attach/content-type
                   ::hs-attach/content-length
                   ::hs-attach/content]))

;; Statement-to-Actor
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Actor', 'Object', 'Authority', 'Instructor', 'Team',
;;                       'SubActor', 'SubObject', 'SubAuthority', 'SubInstructor', 'SubTeam')
;;                 NOT NULL
;; - actor_ifi:    STRING NOT NULL FOREIGN KEY

(def statement-to-actor-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-actor/usage
                   ::hs-actor/actor-ifi]))

;; Statement-to-Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Object', 'Category', 'Grouping', 'Parent', 'Other',
;;                       'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther')
;;                 NOT NULL
;; - activity_iri: STRING NOT NULL FOREIGN KEY

(def statement-to-activity-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-activ/usage
                   ::hs-activ/activity-iri]))

;; Putting it all together
(def statement-insert-seq-spec
  (s/cat
   :statement-input statement-insert-spec
   :actor-inputs (s/* actor-insert-spec)
   :activity-inputs (s/* activity-insert-spec)
   :stmt-actor-inputs (s/* statement-to-actor-insert-spec)
   :stmt-activity-inputs (s/* statement-to-activity-insert-spec)))

(def attachment-insert-seq-spec
  (s/* attachment-insert-spec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def statement-query-spec
  (s/keys :opt-un [::statement-id
                   ::voided?
                   ::verb-iri
                   ::registration
                   ::since
                   ::until
                   ::limit
                   ::ascending?
                   ::related-actors?
                   ::related-activities?
                   ::hs-actor/actor-ifi
                   ::hs-activ/activity-iri]))

(def agent-query-spec
  (s/keys :req-un [::hs-actor/agent-ifi]))

(def activity-query-spec
  (s/keys :req-un [::hs-activ/activity-iri]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State-Document
;; - id:            SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - state_id:      STRING NOT NULL
;; - activity_iri:  STRING NOT NULL
;; - agent_ifi:     STRING NOT NULL
;; - registration:  UUID
;; - last_modified: TIMESTAMP NOT NULL
;; - document:      BINARY NOT NULL

(def state-doc-insert-spec
  (s/keys :req-un [::primary-key
                   ::state-id
                   ::hs-activ/activity-iri
                   ::hs-actor/agent-ifi
                   ::?registration
                   ::last-modified
                   ::document]))

;; Agent-Profile-Document
;; - id:            SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - profile_id:    STRING NOT NULL
;; - agent_ifi:     STRING NOT NULL
;; - last_modified: TIMESTAMP NOT NULL
;; - document:      BINARY NOT NULL

(def agent-profile-doc-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::hs-actor/agent-ifi
                   ::last-modified
                   ::document]))

;; Activity-Profile-Resource
;; - id:            SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - profile_id:    STRING NOT NULL
;; - activity_iri:  STRING NOT NULL
;; - last_modified: TIMESTAMP NOT NULL
;; - document:      BINARY NOT NULL

(def activity-profile-doc-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::hs-activ/activity-iri
                   ::last-modified
                   ::document]))

;; Putting it all together
;; NOTE: need to call s/nonconforming to make it work with s/fdef's :fn

(def document-insert-spec
  (s/nonconforming
   (s/or :state state-doc-insert-spec
         :agent-profile agent-profile-doc-insert-spec
         :activity-profile activity-profile-doc-insert-spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Queries + Deletions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Document queries/deletions

(def state-doc-input-spec
  (s/keys :req-un [::hs-activ/activity-iri
                   ::hs-actor/agent-ifi
                   ::state-id
                   ::?registration]))

(def agent-profile-doc-input-spec
  (s/keys :req-un [::hs-actor/agent-ifi
                   ::profile-id]))

(def activity-profile-doc-input-spec
  (s/keys :req-un [::hs-activ/activity-iri
                   ::profile-id]))

(def document-input-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-input-spec
         :agent-profile agent-profile-doc-input-spec
         :activity-profile activity-profile-doc-input-spec)))

;; Document multi-query/delete

(def state-doc-multi-input-spec
  (s/keys :req-un [::hs-activ/activity-iri
                   ::hs-actor/agent-ifi
                   ::?registration]))

;; Document ID queries

(def state-doc-ids-input-spec
  (s/keys :req-un [::hs-activ/activity-iri
                   ::hs-actor/agent-ifi
                   ::?registration]
          :opt-un [::since]))

(def agent-profile-doc-ids-input-spec
  (s/keys :req-un [:hs-actor/agent-ifi]
          :opt-un [::since]))

(def activity-profile-doc-ids-input-spec
  (s/keys :req-un [::hs-activ/activity-iri]
          :opt-un [::since]))

(def document-ids-query-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-ids-input-spec
         :agent-profile agent-profile-doc-ids-input-spec
         :activity-profile activity-profile-doc-ids-input-spec)))
