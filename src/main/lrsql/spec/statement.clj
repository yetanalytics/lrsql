(ns lrsql.spec.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [xapi-schema.spec.resources :as xsr]
            [com.yetanalytics.lrs.spec.common :as sc]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common     :as c]
            [lrsql.spec.activity   :as hs-activ]
            [lrsql.spec.actor      :as hs-actor]
            [lrsql.spec.attachment :as hs-attach]
            [lrsql.spec.authority  :as hs-auth]
            [lrsql.util.statement :refer [prepare-statement]]))

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
(s/def ::timestamp c/instant-spec)

;; Registration
(s/def ::registration (s/nilable uuid?))

;; Verb
(s/def ::verb-iri :verb/id)
(s/def ::voided? boolean?)
(s/def ::voiding? boolean?)

;; Statement
;; JSON string version: (make-str-spec ::xs/statement u/parse-json u/write-json)
(s/def ::payload ::xs/statement)

(s/def ::reaction-id (s/nilable uuid?))
(s/def ::trigger-id (s/nilable ::c/statement-id))

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
;; - timestamp:        TIMESTAMP NOT NULL
;; - stored:           TIMESTAMP NOT NULL

(s/def ::statement-input
  (s/keys :req-un [::c/primary-key
                   ::statement-id
                   ::statement-ref-id          ; nilable, not in table
                   ::registration              ; nilable
                   ::verb-iri
                   ::voided?
                   ::voiding?                  ; not in table
                   ::hs-attach/attachment-shas ; not in table
                   ::payload
                   ::timestamp
                   ::stored
                   ::reaction-id
                   ::trigger-id]))

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
    #(sgen/fmap (partial apply (partial prepare-statement "1.0.3"))
                (s/gen (s/tuple ::xs/agent
                                ::xs/statement)))))

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
         (s/coll-of insert-statement-input-spec :gen-max 5)
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

;; The `:from` param is a custom parameter that is used by SQL LRS. However,
;; since it is not a param specified in the xAPI. Because of how the spec is
;; structured, however, we basically need to redefine the entire the entire
;; params spec just to add a new spec. Fortunately, though, it is easy to
;; redefine specs thanks to how spec keywords work.

;; The spec definitions are based on xapi-schema.resources:
;; https://github.com/yetanalytics/xapi-schema/blob/master/src/xapi_schema/spec/resources.cljc#L65-L157

(s/def :xapi.statements.GET.request.params/from
  ::xs/uuid)

(defmulti query-type
  #(if (xsr/singular-query? %)
     :xapi.statements.GET.request.params/singular
     :xapi.statements.GET.request.params/multiple))

(defmethod query-type :xapi.statements.GET.request.params/singular [_]
  (s/keys :req-un [(or :xapi.statements.GET.request.params/statementId
                       :xapi.statements.GET.request.params/voidedStatementId)]
          :opt-un [:xapi.statements.GET.request.params/format
                   :xapi.statements.GET.request.params/attachments]))

(defmethod query-type :xapi.statements.GET.request.params/multiple [_]
  (s/keys :opt-un [:xapi.statements.GET.request.params/agent
                   :xapi.statements.GET.request.params/verb
                   :xapi.statements.GET.request.params/activity
                   :xapi.statements.GET.request.params/registration
                   :xapi.statements.GET.request.params/related_activities
                   :xapi.statements.GET.request.params/related_agents
                   :xapi.statements.GET.request.params/since
                   :xapi.statements.GET.request.params/until
                   :xapi.statements.GET.request.params/limit
                   :xapi.statements.GET.request.params/format
                   :xapi.statements.GET.request.params/attachments
                   :xapi.statements.GET.request.params/ascending
                   ;; NEW SPEC
                   :xapi.statements.GET.request.params/from]))

(s/def :xapi.statements.GET.request/params
  (s/multi-spec query-type (fn [gen-val _] gen-val)))

;; See: :com.yetanalytics.lrs.protocol/get-statement-params
(s/def ::get-statement-params
  (sc/with-conform-gen :xapi.statements.GET.request/params))

;; This is to instrument functions, not for production-time validation,
;; which is handled by the `:xapi.statements.GET.request/params` spec.
(s/def ::query-params
  ::get-statement-params)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def statement-query-one-spec
  (s/keys :req-un [::statement-id
                   ::voided?
                   ::format
                   ::attachments?]))

(def statement-query-many-spec
  (s/keys :req-un [::ascending?
                   ::format
                   ::attachments?
                   ::query-params
                   ::more-url-prefix]
          :opt-un [::hs-actor/actor-ifi
                   ::hs-activ/activity-iri
                   ::hs-auth/authority-ifis
                   ::verb-iri
                   ::registration
                   ::related-actors?
                   ::related-activities?
                   ::from
                   ::since
                   ::until
                   ::limit]))

(def statement-query-spec
  (s/or :single   statement-query-one-spec
        :multiple statement-query-many-spec))
