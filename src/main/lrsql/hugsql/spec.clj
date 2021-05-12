(ns lrsql.hugsql.spec
  "Spec for HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.walk :as w]
            [xapi-schema.spec :as xs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Primary key
(s/def ::primary-key uuid?)

;; Statement IDs
(s/def ::statement-id uuid?)
(s/def ::?sub-statement-id (s/nilable uuid?))
(s/def ::?statement-ref-id (s/nilable uuid?))

;; Timestamp
(s/def ::timestamp inst?)
(s/def ::stored inst?)

;; Registration
(s/def ::?registration (s/nilable uuid?))

;; Verb
(s/def ::verb-iri :verb/id)
(s/def ::voided? boolean?)

;; Statement
(s/def ::payload ::xs/statement)

;; Activity
(s/def :lrsql.hugsql.spec.activity/activity-iri :activity/id)
(s/def :lrsql.hugsql.spec.activity/usage
  #{"Object", "Category", "Grouping", "Parent", "Other"})
(s/def :lrsql.hugsql.spec.activity/payload ::xs/activity)

;; Agent
(s/def :lrsql.hugsql.spec.agent/?name (s/nilable string?))
(s/def :lrsql.hugsql.spec.agent/identified-group? boolean?)

(s/def :lrsql.hugsql.spec.agent/mbox ::xs/mailto-iri)
(s/def :lrsql.hugsql.spec.agent/mbox_sha1sum ::xs/sha1sum)
(s/def :lrsql.hugsql.spec.agent/openid ::xs/openid)
(s/def :lrsql.hugsql.spec.agent/account ::xs/account)
(s/def :lrsql.hugsql.spec.agent/ifi
  (s/with-gen
    (s/and (s/conformer w/keywordize-keys
                        w/stringify-keys)
           (s/keys :req-un [(or :lrsql.hugsql.spec.agent/mbox
                                :lrsql.hugsql.spec.agent/mbox_sha1sum
                                :lrsql.hugsql.spec.agent/openid
                                :lrsql.hugsql.spec.agent/account)]))
   #(sgen/fmap
     w/stringify-keys
     (s/gen
      (s/or :mbox
            (s/keys :req-un [:lrsql.hugsql.spec.agent/mbox])
            :mbox-sha1sum
            (s/keys :req-un [:lrsql.hugsql.spec.agent/mbox_sha1sum])
            :openid
            (s/keys :req-un [:lrsql.hugsql.spec.agent/openid])
            :account
            (s/keys :req-un [:lrsql.hugsql.spec.agent/account]))))))

(s/def :lrsql.hugsql.spec.agent/usage
  #{"Actor" "Object" "Authority" "Instructor" "Team"})

;; Attachment
(s/def :lrsql.hugsql.spec.attachment/attachment-sha :attachment/sha2)
(s/def :lrsql.hugsql.spec.attachment/content-type string?)
(s/def :lrsql.hugsql.spec.attachment/file-url ::xs/irl)
(s/def :lrsql.hugsql.spec.attachment/payload any?) ; TODO

;; Document
(s/def ::state-id string?)
(s/def ::profile-id string?)
(s/def ::activity-id :lrsql.hugsql.spec.activity/activity-iri)
(s/def ::agent-id :lrsql.hugsql.spec.agent/ifi)

(s/def ::last-modified inst?)
(s/def ::document any?) ; TODO: `binary?` predicate

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID UNIQUE KEY NOT NULL
;; - SubStatementID: UUID
;; - StatementRefID: UUID
;; - Timestamp: TIMESTAMP NOT NULL
;; - Stored: TIMESTAMP NOT NULL
;; - Registration: UUID
;; - VerbID: STRING NOT NULL
;; - IsVoided: BOOLEAN NOT NULL DEFAULT FALSE
;; - Payload: JSON NOT NULL

(def statement-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   ::?sub-statement-id
                   ::?statement-ref-id
                   ::timestamp
                   ::stored
                   ::?registration
                   ::verb-iri
                   ::voided?
                   ::payload]))

;; /* Need explicit properties for querying Agents Resource */
;; Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - Name: STRING
;; - IFI: JSON -- Map between IFI type and value
;; - IsIdentifiedGroup: BOOLEAN NOT NULL DEFAULT FALSE -- Treat Identified Groups as Agents

(def agent-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.agent/?name
                   :lrsql.hugsql.spec.agent/ifi
                   :lrsql.hugsql.spec.agent/identified-group?]))

;; Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ActivityIRI: STRING UNIQUE KEY NOT NULL
;; - Payload: JSON NOT NULL

(def activity-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.activity/activity-iri
                   :lrsql.hugsql.spec.activity/payload]))

;; Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - SHA2: STRING UNIQUE KEY NOT NULL
;; - ContentType: STRING NOT NULL
;; - FileURL: STRING NOT NULL -- Either an external URL or the URL to a LRS location
;; - Payload: BINARY NOT NULL

(def attachment-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.attachment/attachment-sha
                   :lrsql.hugsql.spec.attachment/content-type
                   ; :lrsql.hugsql.spec.attachment/file-url TODO
                   :lrsql.hugsql.spec.attachment/payload]))

;; Statement-to-Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL
;; - AgentIFI: JSON NOT NULL

(def statement-to-agent-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.agent/usage
                   :lrsql.hugsql.spec.agent/ifi]))

;; Statement-to-Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Object', 'Category', 'Grouping', 'Parent', 'Other') NOT NULL
;; - ActivityIRI: STRING NOT NULL

(def statement-to-activity-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.activity/usage
                   :lrsql.hugsql.spec.activity/activity-iri]))

;; Statement-to-Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID NOT NULL
;; - AttachemntSHA2: STRING NOT NULL

(def statement-to-attachment-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.attachment/attachment-sha]))

;; Putting it all together
(def inputs-seq-spec
  (s/cat
   :statement-input statement-insert-spec
   :agent-inputs (s/* agent-insert-spec)
   :activity-inputs (s/* activity-insert-spec)
   :stmt-agent-inputs (s/* statement-to-agent-insert-spec)
   :stmt-activity-inputs (s/* statement-to-activity-insert-spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StateID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - Registration: UUID
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL

(def state-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::state-id
                   ::activity-id
                   ::agent-id
                   ::?registration
                   ::last-modified
                   ::document]))

;; Agent-Profile-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL

(def agent-profile-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::agent-id
                   ::last-modified
                   ::document]))

;; Activity-Profile-Resource
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL

(def activity-profile-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::activity-id
                   ::last-modified
                   ::document]))

;; TODO
;; Canonical-Language-Maps
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - IRI: STRING UNIQUE KEY NOT NULL
;; - LangTag: STRING NOT NULL
;; - Value: STRING NOT NULL

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Change strings based on SQL implementation (currently H2 only)

(s/def ::statement-id-snip
  (s/cat :query #(= % "statement_id = ?")
         :statement-id ::statement-id))

(s/def ::is-voided-snip
  (s/cat :query #(= % "is_voided = ?")
         :voided? ::voided?))

(s/def ::verb-iri-snip
  (s/cat :query #(= % "verb_iri = ?")
         :verb-iri ::verb-iri))

(s/def ::registration-snip
  (s/cat :query #(= % "registration = ?")
         :registration ::?registration))

(s/def ::timestamp-since-snip
  (s/cat :query #(= % "stored > ?")
         :since ::stored))

(s/def ::timestamp-until-snip
  (s/cat :query #(= % "stored <= ?")
         :until ::stored))

(def ^:private stmt-agent-join-command
  (str "INNER JOIN statement_to_agent\n"
       "  ON statement_id = statement_to_agent.statement_id\n"
       "  AND statement_to_agent.agent_ifi = ?"))

(s/def ::statement-to-agent-join-snip
  (s/cat :command
         (s/alt :actor
                #(= % (str stmt-agent-join-command
                           "\n  AND statement_to_agent.usage = 'Actor'"))
                :broad
                #(= % stmt-agent-join-command))
         :agent-ifi
         :lrsql.hugsql.spec.agent/ifi))

(def ^:private stmt-activity-join-command
  (str "INNER JOIN statement_to_activity\n"
       "  ON statement_id = statement_to_activity.statement_id\n"
       "  AND statement_to_activity.activity_iri = ?"))

(s/def ::statement-to-activity-join-snip
  (s/cat :command
         (s/alt :object
                #(= % (str stmt-activity-join-command
                           "\n  AND statement_to_activity.usage = 'Object'"))
                :broad
                #(= % stmt-activity-join-command))
         :activity-iri
         :lrsql.hugsql.spec.activity/activity-iri))

(s/def ::limit-snip
  (s/cat :command #(= % "LIMIT ?")
         :limit nat-int?))

(def statement-query-spec
  (s/keys :opt-un [::statement-id-snip
                   ::is-voided-snip
                   ::verb-iri-snip
                   ::registration-snip
                   ::timestamp-since-snip
                   ::timestamp-until-snip
                   ::statement-to-agent-join-snip
                   ::statement-to-activity-join-snip
                   ::limit-snip]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
