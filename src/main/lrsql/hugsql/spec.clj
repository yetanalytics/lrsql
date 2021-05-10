(ns lrsql.hugsql.spec
  (:require [clojure.spec.alpha :as s]
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
(s/def :lrsql.hugsql.spec.agent/name string?)
(s/def :lrsql.hugsql.spec.agent/identified-group? boolean?)

(s/def :lrsql.hugsql.spec.agent/mbox ::xs/mailto-iri)
(s/def :lrsql.hugsql.spec.agent/mbox-sha1sum ::xs/sha1sum)
(s/def :lrsql.hugsql.spec.agent/openid ::xs/openid)
(s/def :lrsql.hugsql.spec.agent/account ::xs/account)
(s/def :lrsql.hugsql.spec.agent/ifi
  (s/and (s/conformer (partial xs/conform-ns-map "lrsql.hugsql.spec.agent/mbox")
                      xs/unform-ns-map)
         (s/keys :req-un [(or :lrsql.hugsql.spec.agent/mbox
                              :lrsql.hugsql.spec.agent/mbox-sha1sum
                              :lrsql.hugsql.spec.agent/openid
                              :lrsql.hugsql.spec.agent/account)])))

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

(s/def ::last-modified uuid?)
(s/def ::document any?) ; TODO: `binary?` predicate

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def agent-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.agent/name
                   :lrsql.hugsql.spec.agent/ifi
                   :lrsql.hugsql.spec.agent/identified-group?]))

(def activity-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.activity/activity-iri
                   :lrsql.hugsql.spec.activity/payload]))

(def attachment-insert-spec
  (s/keys :req-un [::primary-key
                   :lrsql.hugsql.spec.attachment/attachment-sha
                   :lrsql.hugsql.spec.attachment/content-type
                   ; :lrsql.hugsql.spec.attachment/file-url TODO
                   :lrsql.hugsql.spec.attachment/payload]))

(def statement-to-agent-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.agent/usage
                   :lrsql.hugsql.spec.agent/ifi]))

(def statement-to-activity-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.activity/usage
                   :lrsql.hugsql.spec.activity/activity-iri]))

(def statement-to-attachment-insert-spec
  (s/keys :req-un [::primary-key
                   ::statement-id
                   :lrsql.hugsql.spec.attachment/attachment-sha]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::state-id
                   ::activity-id
                   ::agent-id
                   ::?registration
                   ::last-modified
                   ::document]))

(def agent-profile-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::agent-id
                   ::last-modified
                   ::document]))

(def activity-profile-document-insert-spec
  (s/keys :req-un [::primary-key
                   ::profile-id
                   ::activity-id
                   ::last-modified
                   ::document]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (s/cat :query #(= % "timestamp > ?")
         :since ::stored))

(s/def ::timestamp-until-snip
  (s/cat :query #(= % "timestamp <= ?")
         :until ::stored))

(def ^:private stmt-agent-join-command
  (str "INNER JOIN statement_to_agent\n"
       "  ON statement.statement_id = statement_to_agent.statement_id\n"
       "  AND ? = statement_to_agent.agent_ifi"))

(s/def ::statement-to-agent-join-snip
  (s/cat :command
         (s/alt :actor
                #(= % (str
                       stmt-agent-join-command
                       "  AND statement_to_agent.usage = 'Actor'"))
                :broad
                #(= % stmt-agent-join-command))
         :agent-ifi
         :lrsql.hugsql.spec.agent/ifi))

(def ^:private stmt-activity-join-command
  (str "INNER JOIN statement_to_activity\n"
       "  ON statement.statement_id = statement_to_activity.statement_id\n"
       "  AND ? = statement_to_activity.activity_iri"))

(s/def ::statement-to-activity-join-snip
  (s/cat :command
         (s/alt :object
                #(= % (str
                       stmt-activity-join-command
                       "  AND statement_to_activity.usage = 'Object'"))
                :broad
                #(= % stmt-activity-join-command))
         :activity-iri
         ::activity-iri))

(s/def ::limit-snip
  (s/cat :command #(= % "LIMIT ?")
         :limit pos-int?))

(def statement-query-spec
  (s/keys :req-un [::statement-id-snip
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
