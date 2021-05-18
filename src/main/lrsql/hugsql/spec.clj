(ns lrsql.hugsql.spec
  "Spec for HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.walk :as w]
            [clojure.data.json :as json]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.util :as u]))

;; TODO: Deal with different encodings for JSON types (e.g. statement payload,
;; activity payload, agent ifi), instead of just H2 strings.

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
(s/def ::since inst?)
(s/def ::until inst?)

;; Registration
(s/def ::registration uuid?)
(s/def ::?registration (s/nilable uuid?))

;; Verb
(s/def ::verb-iri :verb/id)
(s/def ::voided? boolean?)
(s/def ::voiding? boolean?)

;; Statement
(s/def ::payload
  (s/with-gen
    (s/and (s/conformer json/read-str json/write-str) ::xs/statement)
    #(sgen/fmap json/write-str (s/gen ::xs/statement))))

;; Activity
(s/def :lrsql.hugsql.spec.activity/activity-iri :activity/id)
(s/def :lrsql.hugsql.spec.activity/usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})
(s/def :lrsql.hugsql.spec.activity/payload
  (s/with-gen
    (s/and (s/conformer json/read-str json/write-str) ::xs/activity)
    #(sgen/fmap json/write-str (s/gen ::xs/activity))))

;; Agent
(s/def :lrsql.hugsql.spec.agent/?name (s/nilable string?))
(s/def :lrsql.hugsql.spec.agent/identified-group? boolean?)

(s/def :lrsql.hugsql.spec.agent/mbox ::xs/mailto-iri)
(s/def :lrsql.hugsql.spec.agent/mbox_sha1sum ::xs/sha1sum)
(s/def :lrsql.hugsql.spec.agent/openid ::xs/openid)
(s/def :lrsql.hugsql.spec.agent/account ::xs/account)
(s/def :lrsql.hugsql.spec.agent/agent-ifi
  (s/with-gen
      (s/and (s/conformer #(-> % json/read-str w/keywordize-keys)
                          #(-> % w/stringify-keys json/write-str))
             (s/keys :req-un [(or :lrsql.hugsql.spec.agent/mbox
                                  :lrsql.hugsql.spec.agent/mbox_sha1sum
                                  :lrsql.hugsql.spec.agent/openid
                                  :lrsql.hugsql.spec.agent/account)]))
      #(sgen/fmap
        (fn [ifi] (-> ifi w/stringify-keys json/write-str))
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
  #{"Actor" "Object" "Authority" "Instructor" "Team"
    "SubActor" "SubObject" "SubAuthority" "SubInstructor" "SubTeam"})

;; TODO: Check that `bytes?` work with BLOBs

;; Attachment
(s/def :lrsql.hugsql.spec.attachment/attachment-sha :attachment/sha2)
(s/def :lrsql.hugsql.spec.attachment/content-type string?)
(s/def :lrsql.hugsql.spec.attachment/content-length int?)
(s/def :lrsql.hugsql.spec.attachment/file-url ::xs/irl)
(s/def :lrsql.hugsql.spec.attachment/payload bytes?)

;; Document
(s/def ::state-id string?)
(s/def ::profile-id string?)
(s/def ::activity-iri :lrsql.hugsql.spec.activity/activity-iri)
(s/def ::agent-ifi :lrsql.hugsql.spec.agent/agent-ifi)
(s/def ::last-modified inst?)
(s/def ::document bytes?)

;; Query Options
(s/def ::related-agents? boolean?)
(s/def ::related-activities? boolean?)
(s/def ::limit nat-int?)
(s/def ::ascending? boolean?)

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
  "Update the attachments property of each statement so that any sha2 values
   correspond to one in `attachments`."
  [[statements attachments]]
  (let [statements'
        (map (fn [stmt]
               (if (not-empty attachments)
                 (let [{:keys [sha2 contentType length]}
                       (rand-nth attachments)
                       att
                       {"usageType"   "https://example.org/aut"
                        "display"     {"lat" "Lorem Ipsum"}
                        "sha2"        sha2
                        "contentType" contentType
                        "length"      length}]
                   (assoc stmt "attachments" [att]))
                 (assoc stmt "attachments" [])))
             statements)]
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
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID UNIQUE KEY NOT NULL
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
                   ::?statement-ref-id
                   ::timestamp
                   ::stored
                   ::?registration
                   ::verb-iri
                   ::voided?
                   ::voiding?
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
                   :lrsql.hugsql.spec.agent/agent-ifi
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
                   :lrsql.hugsql.spec.attachment/content-length
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
                   :lrsql.hugsql.spec.agent/agent-ifi]))

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
(def statement-inputs-seq-spec
  (s/cat
   :statement-input statement-insert-spec
   :agent-inputs (s/* agent-insert-spec)
   :activity-inputs (s/* activity-insert-spec)
   :stmt-agent-inputs (s/* statement-to-agent-insert-spec)
   :stmt-activity-inputs (s/* statement-to-activity-insert-spec)))

(def attachment-inputs-seq-spec
  (s/cat
   :attachment-inputs (s/* attachment-insert-spec)
   :stmt-attachment-inputs (s/* statement-to-attachment-insert-spec)))

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
                   ::activity-iri
                   ::agent-ifi
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
                   ::agent-ifi
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
                   ::activity-iri
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

(def statement-query-spec
  (s/keys :opt-un [::statement-id
                   ::voided?
                   ::verb-iri
                   ::registration
                   ::since
                   ::until
                   ::limit
                   ::ascending?
                   ::related-agents?
                   ::related-activities?
                   :lrsql.hugsql.spec.agent/agent-ifi
                   :lrsql.hugsql.spec.activity/activity-iri]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Queries + Deletions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state-doc-query-spec
  (s/keys :req-un [::activity-iri
                   ::agent-ifi
                   ::state-id]
          :opt-un [::registration]))

(def state-doc-ids-query-spec
  (s/keys :req-un [::activity-iri
                   ::agent-ifi]
          :opt-un [::registration
                   ::since]))

(def agent-profile-doc-query-spec
  (s/keys :req-un [::agent-ifi
                   ::profile-id]))

(def agent-profile-doc-ids-query-spec
  (s/keys :req-un [::agent-ifi]
          :opt-un [::since]))

(def activity-profile-doc-query-spec
  (s/keys :req-un [::activity-iri
                   ::profile-id]))

(def activity-profile-doc-ids-query-spec
  (s/keys :req-un [::activity-iri]
          :opt-un [::since]))
