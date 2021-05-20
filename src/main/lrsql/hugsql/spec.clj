(ns lrsql.hugsql.spec
  "Spec for HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.data.json :as json]
            [xapi-schema.spec :as xs]
            [xapi-schema.spec.resources :as xres]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.spec.common :as sc]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.util :as u]))

;; TODO: Deal with different encodings for JSON types (e.g. statement payload,
;; activity payload, agent ifi), instead of just H2 strings.

;; TODO
;; Canonical-Language-Maps
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - IRI: STRING UNIQUE KEY NOT NULL
;; - LangTag: STRING NOT NULL
;; - Value: STRING NOT NULL

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Params specs
;; These spec the data received by functions in `lrsql.hugsq.input`.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-statements-params
  ::lrsp/get-statements-params)

(def get-agent-params
  ::lrsp/get-person-params)

;; For some reason this is not defined in lrs.protocol
(def get-activity-params
  (sc/with-conform-gen
    :xapi.activities.GET.request/params))

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

(defn- make-str-spec
  "Make a spec w/ gen capability for strings of a particular format."
  [spec conform-fn unform-fn]
  (s/with-gen
    (s/and string?
           (s/conformer conform-fn unform-fn)
           spec)
    #(sgen/fmap unform-fn
                (s/gen spec))))

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
  (make-str-spec ::xs/statement
                 json/read-str
                 json/write-str))

;; Activity

(s/def :lrsql.hugsql.spec.activity/activity-iri :activity/id)

(s/def :lrsql.hugsql.spec.activity/usage
  #{"Object", "Category", "Grouping", "Parent", "Other"
    "SubObject" "SubCategory" "SubGrouping" "SubParent" "SubOther"})

(s/def :lrsql.hugsql.spec.activity/payload
  (make-str-spec ::xs/activity
                 json/read-str
                 json/write-str))

;; Agent

(s/def :lrsql.hugsql.spec.agent/identified-group? boolean?)

;; "mbox::mailto:foo@example.com"
(def ifi-mbox-spec
  (make-str-spec ::xs/mailto-iri
                 (fn [s] (->> s (re-matches #"mbox::(.*)") second))
                 (fn [s] (->> s (str "mbox::")))))

;; "mbox_sha1sum::123456789ABCDEF123456789ABCDEF123456789A" 
(def ifi-mbox-sha1sum-spec ;
  (make-str-spec ::xs/sha1sum
                 (fn [s] (->> s (re-matches #"mbox_sha1sum::(.*)") second))
                 (fn [s] (->> s (str "mbox_sha1sum::")))))

;; "openid::http://example.org/bar"
(def ifi-openid-spec
  (make-str-spec ::xs/openid
                 (fn [s] (->> s (re-matches #"openid::(.*)") second))
                 (fn [s] (->> s (str "openid::")))))

;; "account::alice@http://example.org"
(def ifi-account-spec
  (make-str-spec ::xs/account
                 (fn [s]
                   (let [[_ nm hp] (re-matches #"account::(.*)@(.*)" s)]
                     {:account/name nm :account/homePage hp}))
                 (fn [{nm "name" hp "homePage"}]
                   (str "account::" nm "@" hp))))

(s/def :lrsql.hugsql.spec.agent/agent-ifi
  (s/or :mbox ifi-mbox-spec
        :mbox-sha1sum ifi-mbox-sha1sum-spec
        :openid ifi-openid-spec
        :account ifi-account-spec))

(s/def :lrsql.hugsql.spec.agent/usage
  #{"Actor" "Object" "Authority" "Instructor" "Team"
    "SubActor" "SubObject" "SubAuthority" "SubInstructor" "SubTeam"})

(s/def :lrsql.hugsql.spec.agent/payload
  :xapi.statements.GET.request.params/agent)

;; For agent queries
(s/def :lrsql.hugsql.spec.agent/agent
  (xres/json
   (s/nonconforming
    (s/or :agent ::xs/agent
          :group ::xs/identified-group))))

;; TODO: Check that `bytes?` work with BLOBs

;; Attachment
(s/def :lrsql.hugsql.spec.attachment/attachment-sha :attachment/sha2)
(s/def :lrsql.hugsql.spec.attachment/content-type string?)
(s/def :lrsql.hugsql.spec.attachment/content-length int?)
(s/def :lrsql.hugsql.spec.attachment/content bytes?)

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
;; - IFI: STRING NOT NULL
;; - IsIdentifiedGroup: BOOLEAN NOT NULL DEFAULT FALSE -- Treat Identified Groups as Agents

(def agent-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.agent/agent-ifi
                   :lrsql.hugsql.spec.agent/identified-group?
                   :lrsql.hugsql.spec.agent/payload]))

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
                   ::statement-id
                   :lrsql.hugsql.spec.attachment/attachment-sha
                   :lrsql.hugsql.spec.attachment/content-type
                   :lrsql.hugsql.spec.attachment/content-length
                   :lrsql.hugsql.spec.attachment/content]))

;; Statement-to-Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL
;; - AgentIFI: STRING NOT NULL

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

;; Putting it all together
(def statement-insert-seq-spec
  (s/cat
   :statement-input statement-insert-spec
   :agent-inputs (s/* agent-insert-spec)
   :activity-inputs (s/* activity-insert-spec)
   :stmt-agent-inputs (s/* statement-to-agent-insert-spec)
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
                   ::related-agents?
                   ::related-activities?
                   :lrsql.hugsql.spec.agent/agent-ifi
                   :lrsql.hugsql.spec.activity/activity-iri]))

(def agent-query-spec
  (s/keys :req-un [:lrsql.hugsql.spec.agent/agent-ifi]))

(def activity-query-spec
  (s/keys :req-un [:lrsql.hugsql.spec.activity/activity-iri]))

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

(def state-doc-insert-spec
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

(def agent-profile-doc-insert-spec
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

(def activity-profile-doc-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::activity-iri
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
  (s/keys :req-un [::activity-iri
                   ::agent-ifi
                   ::state-id
                   ::?registration]))

(def agent-profile-doc-input-spec
  (s/keys :req-un [::agent-ifi
                   ::profile-id]))

(def activity-profile-doc-input-spec
  (s/keys :req-un [::activity-iri
                   ::profile-id]))

(def document-input-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-input-spec
         :agent-profile agent-profile-doc-input-spec
         :activity-profile activity-profile-doc-input-spec)))

;; Document multi-query/delete

(def state-doc-multi-input-spec
  (s/keys :req-un [::activity-iri
                   ::agent-ifi
                   ::?registration]))

;; Document ID queries

(def state-doc-ids-input-spec
  (s/keys :req-un [::activity-iri
                   ::agent-ifi
                   ::?registration]
          :opt-un [::since]))

(def agent-profile-doc-ids-input-spec
  (s/keys :req-un [::agent-ifi]
          :opt-un [::since]))

(def activity-profile-doc-ids-input-spec
  (s/keys :req-un [::activity-iri]
          :opt-un [::since]))

(def document-ids-query-spec
  (s/nonconforming ; needed to make s/fdef work
   (s/or :state state-doc-ids-input-spec
         :agent-profile agent-profile-doc-ids-input-spec
         :activity-profile activity-profile-doc-ids-input-spec)))
