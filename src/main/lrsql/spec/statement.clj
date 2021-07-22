(ns lrsql.spec.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common     :as c]
            [lrsql.spec.activity   :as hs-activ]
            [lrsql.spec.actor      :as hs-actor]
            [lrsql.spec.attachment :as hs-attach]
            [lrsql.util.statement :refer [prepare-statement]]))

;; TODO: Deal with different encodings for JSON types (e.g. payloads,
;; actor ifi), instead of just H2 strings.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn statement-backend?
  [bk]
  (satisfies? bp/StatementBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Primary key
(s/def ::primary-key uuid?)

;; Statement IDs
(s/def ::statement-id ::c/statement-id)
(s/def ::statement-ref-id (s/nilable ::statement-id))
(s/def ::ancestor-id ::statement-id)
(s/def ::descendant-id ::statement-id)

;; Timestamp
(s/def ::stored c/instant-spec)

;; Registration
;; TODO: Make a separate nilable version
(s/def ::registration (s/nilable uuid?))

;; Verb
(s/def ::verb-iri :verb/id)
(s/def ::voided? boolean?)
(s/def ::voiding? boolean?)

;; Statement
;; JSON string version: (make-str-spec ::xs/statement u/parse-json u/write-json)
(s/def ::payload ::xs/statement)

;; Query-specific Params
(s/def ::related-actors? boolean?)
(s/def ::related-activities? boolean?)

(s/def ::since uuid?)
(s/def ::until uuid?)
(s/def ::from uuid?)

(s/def ::limit nat-int?)
(s/def ::ascending? boolean?)

(s/def ::format #{:ids :exact :canonical})
(s/def ::attachments? boolean?)

(s/def ::more-url-prefix string?)

(def lang-tags-spec
  (s/coll-of ::xs/language-tag :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement
;; - id:               SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id:     UUID NOT NULL UNIQUE KEY
;; - registration:     UUID
;; - verb_iri:         STRING NOT NULL
;; - is_voided:        BOOLEAN NOT NULL DEFAULT FALSE
;; - payload:          JSON NOT NULL

(s/def ::statement-input
  (s/keys :req-un [::c/primary-key
                   ::statement-id
                   ::statement-ref-id          ; nilable, not in table
                   ::registration              ; nilable
                   ::verb-iri
                   ::voided?
                   ::voiding?                  ; not in table
                   ::hs-attach/attachment-shas ; not in table
                   ::payload]))

;; Statement-to-Statement
;; - id:            SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - ancestor_id:   UUID NOT NULL FOREIGN KEY
;; - descendant_id: UUID NOT NULL FOREIGN KEY

(s/def ::stmt-stmt-input
  (s/keys :req-un [::c/primary-key
                   ::ancestor-id
                   ::descendant-id]))

(s/def ::stmt-stmt-inputs
  (s/coll-of ::stmt-stmt-input :gen-max 5))

;; Putting it all together

(def insert-statement-input-spec
  (s/keys :req-un [::statement-input
                   ::hs-actor/actor-inputs
                   ::hs-activ/activity-inputs
                   ::hs-attach/attachment-inputs
                   ::hs-actor/stmt-actor-inputs
                   ::hs-activ/stmt-activity-inputs
                   ::stmt-stmt-inputs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion params spec
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
                        [n :statement-input :attachment-shas]
                        conj
                        sha2)))
         stmt-inputs
         attachments)]
    [stmt-inputs' attachments]))

(def stmt-input-attachments-spec*
  (s/cat :statement-inputs
         (s/coll-of insert-statement-input-spec :min-count 1 :gen-max 5)
         :attachments
         (s/coll-of ::ss/attachment :gen-max 2)))

(def stmt-input-attachments-spec
  (s/with-gen
   stmt-input-attachments-spec*
   #(sgen/fmap update-stmt-input-attachments
               (s/gen stmt-input-attachments-spec*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query params specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::query-params
  (s/merge ::lrsp/get-statements-params
           (s/keys :req-un [::limit]
                   :opt-un [::more-url-prefix])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def statement-query-one-spec
  (s/keys :req-un [::statement-id
                   ::voided?
                   ::format
                   ::attachments?]))

(def statement-query-many-spec
  (s/keys :req-un [::limit
                   ::ascending?
                   ::format
                   ::attachments?
                   ::query-params
                   ::more-url-prefix]
          :opt-un [::hs-actor/actor-ifi
                   ::hs-actor/activity-iri
                   ::verb-iri
                   ::registration
                   ::related-actors?
                   ::related-activities?
                   ::from
                   ::since
                   ::until]))

(def statement-query-spec
  (s/or :single   statement-query-one-spec
        :multiple statement-query-many-spec))
