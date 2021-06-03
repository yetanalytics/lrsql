(ns lrsql.hugsql.spec.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.spec.activity   :as hs-activ]
            [lrsql.hugsql.spec.actor      :as hs-actor]
            [lrsql.hugsql.spec.attachment :as hs-attach]
            [lrsql.hugsql.spec.util      :refer [make-str-spec]]
            [lrsql.hugsql.util.statement :refer [prepare-statement]]))

;; TODO: Deal with different encodings for JSON types (e.g. payloads,
;; actor ifi), instead of just H2 strings.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params specs
;; These spec the data received by functions in `lrsql.hugsq.input`.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-statements-params
  ::lrsp/get-statements-params)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Primary key
(s/def ::primary-key uuid?)

;; Statement IDs
(s/def ::statement-id uuid?)
(s/def ::?statement-ref-id (s/nilable ::statement-id))
(s/def ::ancestor-id ::statement-id)
(s/def ::descendant-id ::statement-id)

;; Timestamp
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
  ::xs/statement
  #_(make-str-spec ::xs/statement u/parse-json u/write-json))

;; Query-specific Params
(s/def ::related-actors? boolean?)
(s/def ::related-activities? boolean?)
(s/def ::since inst?)
(s/def ::until inst?)
(s/def ::limit nat-int?)
(s/def ::ascending? boolean?)
(s/def ::from uuid?)
(s/def ::format #{:ids :exact :canonical})
(s/def ::attachments? boolean?)

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
    #(sgen/fmap prepare-statement
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
;; - stored:           TIMESTAMP NOT NULL
;; - registration:     UUID
;; - verb_iri:         STRING NOT NULL
;; - is_voided:        BOOLEAN NOT NULL DEFAULT FALSE
;; - payload:          JSON NOT NULL

(def statement-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::?statement-ref-id
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
;; - contents:       BINARY NOT NULL

(def attachment-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-attach/attachment-sha
                   ::hs-attach/content-type
                   ::hs-attach/content-length
                   ::hs-attach/contents]))

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

(def statement-to-statement-insert-spec
  (s/keys :req-un [::primary-key
                   ::ancestor-id
                   ::descendant-id]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def statement-query-one-spec
  (s/keys :req-un [::statement-id
                   ::voided?]
          :opt-un [::format
                   ::attachments?]))

(def statement-query-many-spec
  (s/keys :opt-un [::from
                   ::since
                   ::until
                   ::limit
                   ::ascending?
                   ::verb-iri
                   ::registration
                   ::related-actors?
                   ::related-activities?
                   ::hs-actor/actor-ifi
                   ::hs-activ/activity-iri
                   ::format
                   ::attachments?]))

(def statement-query-spec
  (s/or :one  statement-query-one-spec
        :many statement-query-many-spec))
