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

;; Attachments
(s/def ::?attachment-shas
  (s/nilable (s/coll-of ::hs-attach/attachment-sha
                        :kind set?
                        :gen-max 5)))

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

(s/def ::statement-input
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::?statement-ref-id
                   ::stored
                   ::?registration
                   ::verb-iri
                   ::voided?
                   ::voiding?
                   ::?attachment-shas
                   ::payload]))

;; In this context, "Actor" is a catch-all term to refer to both Agents and
;; Identified Groups, not the Actor object within Statements.

;; Actor
;; - id:          SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - actor_ifi:   STRING NOT NULL UNIQUE KEY
;; - actor_type:  ENUM ('Agent', 'Group') NOT NULL
;; - payload:     JSON NOT NULL

(s/def ::actor-input
  (s/keys :req-un [::primary-key
                   ::hs-actor/actor-ifi
                   ::hs-actor/actor-type
                   ::hs-actor/payload]))

(s/def ::actor-inputs
  (s/coll-of ::actor-input :gen-max 5))

;; Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - activity_iri: STRING NOT NULL UNIQUE KEY
;; - payload:      JSON NOT NULL

(s/def ::activity-input
  (s/keys :req-un [::primary-key
                   ::hs-activ/activity-iri
                   ::hs-activ/payload]))

(s/def ::activity-inputs
  (s/coll-of ::activity-input :gen-max 5))

;; Attachment
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_key:  UUID NOT NULL FOREIGN KEY
;; - attachment_sha: STRING NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - contents:       BINARY NOT NULL

(s/def ::attachment-input
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-attach/attachment-sha
                   ::hs-attach/content-type
                   ::hs-attach/content-length
                   ::hs-attach/contents]))

(s/def ::attachment-inputs
  (s/coll-of ::attachment-input :gen-max 5))

;; Statement-to-Actor
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Actor', 'Object', 'Authority', 'Instructor', 'Team',
;;                       'SubActor', 'SubObject', 'SubAuthority', 'SubInstructor', 'SubTeam')
;;                 NOT NULL
;; - actor_ifi:    STRING NOT NULL FOREIGN KEY

(s/def ::stmt-actor-input
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-actor/usage
                   ::hs-actor/actor-ifi]))

(s/def ::stmt-actor-inputs
  (s/coll-of ::stmt-actor-input :gen-max 5))

;; Statement-to-Activity
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Object', 'Category', 'Grouping', 'Parent', 'Other',
;;                       'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther')
;;                 NOT NULL
;; - activity_iri: STRING NOT NULL FOREIGN KEY

(s/def ::stmt-activity-input
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::hs-activ/usage
                   ::hs-activ/activity-iri]))

(s/def ::stmt-activity-inputs
  (s/coll-of ::stmt-activity-input :gen-max 5))

;; Statement-to-Statement
;; - id:            SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - ancestor_id:   UUID NOT NULL FOREIGN KEY
;; - descendant_id: UUID NOT NULL FOREIGN KEY

(s/def ::stmt-stmt-input
  (s/keys :req-un [::primary-key
                   ::ancestor-id
                   ::descendant-id]))

(s/def ::stmt-stmt-inputs
  (s/coll-of ::stmt-stmt-input :gen-max 5))

;; Putting it all together

(def statement-insert-map-spec
  (s/keys :req-un [::statement-input
                   ::actor-inputs
                   ::activity-inputs
                   ::attachment-inputs
                   ::stmt-actor-inputs
                   ::stmt-activity-inputs
                   ::stmt-stmt-inputs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Function Parameters
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

(defn- update-stmt-input-attachments
  [[stmt-inputs attachments]]
  (let [num-stmts
        (count stmt-inputs)
        stmt-inputs'
        (reduce
         (fn [stmt-inputs {:keys [sha2] :as _attachment}]
           (let [n (rand-int num-stmts)]
             (update-in stmt-inputs
                        [n :statement-input :?attachment-shas]
                        (fn [shas s] (if shas (conj s) #{s}))
                        sha2)))
         stmt-inputs
         attachments)]
    [stmt-inputs' attachments]))

(def stmt-input-attachments-spec*
  (s/cat :statement-inputs
         (s/coll-of statement-insert-map-spec :min-count 1 :gen-max 5)
         :attachments
         (s/coll-of ::ss/attachment :gen-max 2)))

(def stmt-input-attachments-spec
  (s/with-gen
   stmt-input-attachments-spec*
   #(sgen/fmap update-stmt-input-attachments
               (s/gen stmt-input-attachments-spec*))))

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
