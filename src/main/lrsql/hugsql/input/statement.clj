(ns lrsql.hugsql.input.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as cset]
            [com.yetanalytics.lrs.pedestal.interceptor.xapi.statements.attachment :as xsa]
            ;; Specs
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec.statement :as hs]
            ;; Utils
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.util.actor :as ua]
            [lrsql.hugsql.util.statement :as us]))

(def voiding-verb "http://adlnet.gov/expapi/verbs/voided")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor/Activity Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef actor-insert-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::hs/actor-input))

(defn actor-insert-input
  "Given `actor`, construct the input for `functions/insert-actor!`, or nil
   if it does not have an IFI."
  [actor]
  (when-some [ifi-str (ua/actor->ifi actor)]
    {:table       :actor
     :primary-key (u/generate-squuid)
     :actor-ifi   ifi-str
     :actor-type  (get actor "objectType" "Agent")
     :payload     actor}))

(s/fdef group-insert-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable ::hs/actor-inputs))

(defn group-insert-input
  "Given `actor`, return a coll of actor inputs, or nil if `actor` is not
   a Group or has no members. Both Anonymous and Identified Group members
   count."
  [actor]
  ;; Use let-binding in order to avoid cluttering args list
  (let [{obj-type "objectType" members  "member"} actor]
    (when (and (= "Group" obj-type) (not-empty members))
      (map actor-insert-input members))))

(s/fdef activity-insert-input
  :args (s/cat :activity ::xs/activity)
  :ret ::hs/activity-input)

(defn activity-insert-input
  "Given `activity`, construct the input for `functions/insert-activity!`."
  [activity]
  {:table        :activity
   :primary-key  (u/generate-squuid)
   :activity-iri (get activity "id")
   :payload      activity})

(s/fdef statement-to-actor-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :actor-usage :lrsql.hugsql.spec.actor/usage
               :actor-input ::hs/actor-input)
  :ret ::hs/stmt-actor-input)

(defn statement-to-actor-insert-input
  "Given `statement-id`, `actor-usage` and the input to `f/insert-actor!`,
   return the input for `functions/insert-statement-to-actor!`."
  [statement-id actor-usage {actor-ifi :actor-ifi}]
  {:table        :statement-to-actor
   :primary-key  (u/generate-squuid)
   :statement-id statement-id
   :usage        actor-usage
   :actor-ifi    actor-ifi})

(s/fdef statement-to-activity-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :activity-usage :lrsql.hugsql.spec.activity/usage
               :activity-input ::hs/activity-input)
  :ret ::hs/stmt-activity-input)

(defn statement-to-activity-insert-input
  "Given `statement-id`, `activity-usage` and the HugSql params map for
   `insert-activity`, return the HugSql params map for
   `functions/insert-statement-to-activity!`."
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (u/generate-squuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Deal with contextAgents, contextGroups, and any other properties
;; in version 2.0

(defn- statement-actor-insert-inputs
  "Helper to construct the `functions/insert-actor!` inputs for a statement's
   Agents and Groups."
  [stmt-id stmt-act stmt-obj ?stmt-auth ?stmt-inst ?stmt-team sql-enums]
  (let [;; Destructuring
        {:keys [act-enum obj-enum auth-enum inst-enum team-enum]}
        sql-enums
        {stmt-obj-type "objectType" :or {stmt-obj-type "Activity"}}
        stmt-obj
        ;; Object Type
        actor-obj?
        (boolean (#{"Agent" "Group"} stmt-obj-type))
        ;; Statement Actors
        ?act-input  (actor-insert-input stmt-act)
        ?obj-input  (when actor-obj? (actor-insert-input stmt-obj))
        ?auth-input (when ?stmt-auth (actor-insert-input ?stmt-auth))
        ?inst-input (when ?stmt-inst (actor-insert-input ?stmt-inst))
        ?team-input (when ?stmt-team (actor-insert-input ?stmt-team))
        ;; Member Actors
        ?act-mem-inputs  (group-insert-input stmt-act)
        ?obj-mem-inputs  (when actor-obj? (group-insert-input stmt-obj))
        ?auth-mem-inputs (when ?stmt-auth (group-insert-input ?stmt-auth))
        ?inst-mem-inputs (when ?stmt-inst (group-insert-input ?stmt-inst))
        ?team-mem-inputs (when ?stmt-team (group-insert-input ?stmt-team))
        ;; Actor Inputs
        actor-inputs (cond-> []
                       ;; Statememt Actors
                       ?act-input  (conj ?act-input)
                       ?obj-input  (conj ?obj-input)
                       ?auth-input (conj ?auth-input)
                       ?inst-input (conj ?inst-input)
                       ?team-input (conj ?team-input)
                       ;; Member Actors
                       ?act-mem-inputs  (concat ?act-mem-inputs)
                       ?obj-mem-inputs  (concat ?obj-mem-inputs)
                       ?auth-mem-inputs (concat ?auth-mem-inputs)
                       ?inst-mem-inputs (concat ?inst-mem-inputs)
                       ?team-mem-inputs (concat ?team-mem-inputs))
        ;; Statement to Actor Inputs
        actor->link (partial statement-to-actor-insert-input stmt-id)
        stmt-actors (cond-> []
                      ;; Statement Actors
                      ?act-input
                      (conj (actor->link act-enum ?act-input))
                      ?obj-input
                      (conj (actor->link obj-enum ?obj-input))
                      ?auth-input
                      (conj (actor->link auth-enum ?auth-input))
                      ?inst-input
                      (conj (actor->link inst-enum ?inst-input))
                      ?team-input
                      (conj (actor->link team-enum ?team-input))
                      ;; Member Actors
                      ?act-mem-inputs
                      (concat (map (partial actor->link act-enum)
                                   ?act-mem-inputs))
                      ?obj-mem-inputs
                      (concat (map (partial actor->link obj-enum)
                                   ?obj-mem-inputs))
                      ?auth-mem-inputs
                      (concat (map (partial actor->link auth-enum)
                                   ?auth-mem-inputs))
                      ?inst-mem-inputs
                      (concat (map (partial actor->link inst-enum)
                                   ?inst-mem-inputs))
                      ?team-mem-inputs
                      (concat (map (partial actor->link team-enum)
                                   ?team-mem-inputs)))]
    [actor-inputs stmt-actors]))

(defn- statement-activity-insert-inputs
  "Helper to construct the `functions/insert-activity!` inputs for a statement's
   Activities."
  [stmt-id stmt-obj ?stmt-ctx-acts sql-enums]
  (let [;; Destructuring
        {:keys [obj-enum cat-enum grp-enum prt-enum oth-enum]}
        sql-enums
        {stmt-obj-type "objectType" :or {stmt-obj-type "Activity"}}
        stmt-obj
        {?cat-acts "category"
         ?grp-acts "grouping"
         ?prt-acts "parent"
         ?oth-acts "other"}
        ?stmt-ctx-acts
        ;; Object Type
        activity-obj?
        (boolean (#{"Activity"} stmt-obj-type))
        ;; Statement Activities
        ?obj-act-in  (when activity-obj? (activity-insert-input stmt-obj))
        ?cat-acts-in (when ?cat-acts (map activity-insert-input ?cat-acts))
        ?grp-acts-in (when ?grp-acts (map activity-insert-input ?grp-acts))
        ?prt-acts-in (when ?prt-acts (map activity-insert-input ?prt-acts))
        ?oth-acts-in (when ?oth-acts (map activity-insert-input ?oth-acts))
        ;; Activity Inputs
        act-inputs (cond-> []
                     ?obj-act-in  (conj ?obj-act-in)
                     ?cat-acts-in (concat ?cat-acts-in)
                     ?grp-acts-in (concat ?grp-acts-in)
                     ?prt-acts-in (concat ?prt-acts-in)
                     ?oth-acts-in (concat ?oth-acts-in))
        ;; Statement to Activity Enums
        act->link (partial statement-to-activity-insert-input stmt-id)
        stmt-acts (cond-> []
                    ?obj-act-in
                    (conj (act->link obj-enum ?obj-act-in))
                    ?cat-acts-in
                    (concat (map (partial act->link cat-enum) ?cat-acts-in))
                    ?grp-acts-in
                    (concat (map (partial act->link grp-enum) ?grp-acts-in))
                    ?prt-acts-in
                    (concat (map (partial act->link prt-enum) ?prt-acts-in))
                    ?oth-acts-in
                    (concat (map (partial act->link oth-enum) ?oth-acts-in)))]
    [act-inputs stmt-acts]))

(defn- sub-statement-insert-inputs
  [stmt-id sub-statement]
  (let [;; SubStatement Properties
        {sub-stmt-act  "actor"
         sub-stmt-obj  "object"
         ?sub-stmt-ctx "context"}
        sub-statement
        {?sub-stmt-ctx-acts "contextActivities"
         ?sub-stmt-inst     "instructor"
         ?sub-stmt-team     "team"}
        ?sub-stmt-ctx
        ;; Actor Inputs
        [actor-inputs stmt-actor-inputs]
        (statement-actor-insert-inputs stmt-id
                                       sub-stmt-act
                                       sub-stmt-obj
                                       nil ; No Authority for SubStatements
                                       ?sub-stmt-inst
                                       ?sub-stmt-team
                                       {:act-enum  "SubActor"
                                        :obj-enum  "SubObject"
                                        :inst-enum "SubInstructor"
                                        :team-enum "SubTeam"})
        ;; Activity Inputs
        [activity-inputs stmt-activity-inputs]
        (statement-activity-insert-inputs stmt-id
                                          sub-stmt-obj
                                          ?sub-stmt-ctx-acts
                                          {:obj-enum "SubObject"
                                           :cat-enum "SubCategory"
                                           :grp-enum "SubGrouping"
                                           :prt-enum "SubParent"
                                           :oth-enum "SubOther"})]
    [actor-inputs activity-inputs stmt-actor-inputs stmt-activity-inputs]))

(s/fdef statement-insert-inputs
  :args (s/cat :statement hs/prepared-statement-spec)
  :ret hs/statement-insert-map-spec)

(defn statement-insert-inputs
  "Given `statement`, return a seq of inputs that serve as the input for
   `command/insert-statements!`, starting with the params for
   `functions/insert-statement!`."
  [statement]
  (let [;; Destructuring
        ;; `id`, `stored`, and `authority` should have already been
        ;; set by `prepare-statement`.
        {stmt-id    "id"
         stmt-stor  "stored"
         stmt-act   "actor"
         stmt-vrb   "verb"
         stmt-obj   "object"
         ?stmt-ctx  "context"
         ?stmt-auth "authority"
         ?stmt-atts "attachments"}
        statement
        {stmt-vrb-id "id"}
        stmt-vrb
        {stmt-obj-type "objectType" :or {stmt-obj-type "Activity"}}
        stmt-obj
        {?stmt-ctx-acts "contextActivities"
         ?stmt-inst     "instructor"
         ?stmt-team     "team"
         ?stmt-reg      "registration"}
        ?stmt-ctx
        ;; Revised Statement Properties
        stmt-pk      (-> statement meta :primary-key)
        stmt-id      (u/str->uuid stmt-id)
        stmt-stored  (u/str->time stmt-stor)
        ?stmt-reg    (when ?stmt-reg
                       (u/str->uuid ?stmt-reg))
        ?stmt-ref-id (when (= "StatementRef" stmt-obj-type)
                       (u/str->uuid (get stmt-obj "id")))
        voiding?     (and (some? ?stmt-ref-id) ; should be true but sanity check
                          (= voiding-verb stmt-vrb-id))
        att-shas     (set (concat
                           (when ?stmt-atts
                             (map #(get % "sha2") ?stmt-atts))
                           (when-let [sstmt-atts
                                      (and (= "SubStatement" stmt-obj-type)
                                           (get stmt-obj "attachments"))]
                             (map #(get % "sha2") sstmt-atts))))
        ;; Statement HugSql input
        stmt-input {:table             :statement
                    :primary-key       stmt-pk
                    :statement-id      stmt-id
                    :statement-ref-id  ?stmt-ref-id
                    :stored            stmt-stored
                    :registration      ?stmt-reg
                    :attachment-shas   att-shas
                    :verb-iri          stmt-vrb-id
                    :voided?           false
                    :voiding?          voiding?
                    :payload           statement}
        ;; Actor HugSql Inputs
        [actor-inputs stmt-actor-inputs]
        (statement-actor-insert-inputs stmt-id
                                       stmt-act
                                       stmt-obj
                                       ?stmt-auth
                                       ?stmt-inst
                                       ?stmt-team
                                       {:act-enum  "Actor"
                                        :obj-enum  "Object"
                                        :auth-enum "Authority"
                                        :inst-enum "Instructor"
                                        :team-enum "Team"})
        ;; Activity HugSql Inputs
        [activ-inputs stmt-activ-inputs]
        (statement-activity-insert-inputs stmt-id
                                          stmt-obj
                                          ?stmt-ctx-acts
                                          {:obj-enum "Object"
                                           :cat-enum "Category"
                                           :grp-enum "Grouping"
                                           :prt-enum "Parent"
                                           :oth-enum "Other"})
        ;; SubStatement HugSql Inputs
        [sactor-inputs sactiv-inputs sstmt-actor-inputs sstmt-activ-inputs]
        (when (= "SubStatement" stmt-obj-type)
          (sub-statement-insert-inputs stmt-id stmt-obj))]
    {:statement-input      stmt-input
     :actor-inputs         (concat actor-inputs sactor-inputs)
     :activity-inputs      (concat activ-inputs sactiv-inputs)
     :stmt-actor-inputs    (concat stmt-actor-inputs sstmt-actor-inputs)
     :stmt-activity-inputs (concat stmt-activ-inputs sstmt-activ-inputs)
     :stmt-stmt-inputs     []
     :attachment-inputs    []}))

(s/fdef statements-insert-inputs
  :args (s/cat :statements (s/coll-of hs/prepared-statement-spec
                                      :min-count 1
                                      :gen-max 5))
  :ret (s/coll-of hs/statement-insert-map-spec :min-count 1))

(defn statements-insert-inputs
  "Given the coll `statements`, return a seq of input maps that serve as the
   input for `command/insert-statements!`"
  [statements]
  (map statement-insert-inputs statements))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Descendant Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef descendant-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :desceendant-id ::hs/statement-id)
  :ret ::hs/stmt-stmt-input)

(defn descendant-insert-input
  [statement-id descendant-id]
  {:table         :statement-to-statement
   :primary-key   (u/generate-squuid)
   :descendant-id descendant-id
   :ancestor-id   statement-id})

(s/fdef add-descendant-insert-inputs
  :args (s/cat :input-map hs/statement-insert-map-spec
               :desc-ids  (s/coll-of uuid?))
  :ret  hs/statement-insert-map-spec)

(defn add-descendant-insert-inputs
  [input-map desc-ids]
  (let [stmt-id (-> input-map :statement-input :statement-id)]
    (reduce (fn [input-map' desc-id]
              (update input-map'
                      :stmt-stmt-inputs
                      conj
                      (descendant-insert-input stmt-id desc-id)))
            input-map
            desc-ids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attachment Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Assume that the LRS has already validated that every statement
;; attachment object has a fileUrl or valid SHA2 value.

(s/fdef attachment-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :attachment ::ss/attachment)
  :ret ::hs/attachment-input)

(defn attachment-insert-input
  "Given `statement-id` and `attachment`, construct the input for
   `functions/insert-attachment!`. `statement-id` will be associated with
   `attachment` as a foreign key reference."
  [statement-id attachment]
  (let [{contents     :content
         content-type :contentType
         length       :length
         sha2         :sha2}
        attachment]
    {:table          :attachment
     :primary-key    (u/generate-squuid)
     :statement-id   statement-id
     :attachment-sha sha2
     :content-type   content-type
     :content-length length
     :contents       (u/data->bytes contents)}))

(s/fdef add-attachment-insert-inputs
  :args hs/stmt-input-attachments-spec
  :ret (s/coll-of hs/statement-insert-map-spec))

(def ^:private attachment-emsg
  "There exist Attachments not associated with the Statements in the request.")

;; TODO: The SHA2 hash may result in hash collisions with otherwise-different
;; binary data. May want to investigate further.

(defn add-attachment-insert-inputs
  [input-maps attachments]
  (if (not-empty attachments)
    (let [sha-att-m
          (into {} (map (fn [{sha :sha2 :as att}] [sha att]) attachments))
          shas
          (set (keys sha-att-m))
          result
          (for [imap input-maps
                :let [stmt-id    (-> imap :statement-input :statement-id)
                      ?att-shas  (-> imap :statement-input :attachment-shas)
                      ?stmt-shas (and ?att-shas
                                      (cset/intersection shas ?att-shas))
                      new-imap   (reduce
                                  (fn [imap sha]
                                    (let [att    (sha-att-m sha)
                                          att-in (attachment-insert-input
                                                  stmt-id
                                                  att)]
                                      (update imap
                                              :attachment-inputs
                                              conj
                                              att-in)))
                                  imap
                                  ?stmt-shas)]]
            [new-imap ?stmt-shas])
          added-shas
          (->> result (map second) (filter some?) (apply cset/union))]
      (if-some [diff-sha (not-empty (clojure.set/difference shas
                                                            added-shas))]
        ;; Some attachments weren't included - throw an error
        (throw (ex-info attachment-emsg
                        {:type         ::xsa/statement-attachment-mismatch
                         :attachments  attachments
                         :invalid-shas diff-sha
                         :stmt-inputs  input-maps}))
        ;; All attachments were included - return new stmt inputs
        (map first result)))
    input-maps))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Invalid query params, e.g. those with both a statementId and a
;; voidedStatementId property, or singleton queries with extra params,
;; would have been filtered out earlier by interceptors.

(s/fdef statement-query-input
  :args (s/cat :params ::hs/query-params)
  :ret hs/statement-query-spec)

(defn statement-query-input
  "Construct the input for `command/query-statement!`."
  [{?stmt-id     :statementId
    ?vstmt-id    :voidedStatementId
    ?verb-iri    :verb
    ?actor       :agent
    ?activ-iri   :activity
    ?reg         :registration
    ?rel-activs? :related_activities
    ?rel-actors? :related_agents
    ?since       :since
    ?until       :until
    ?limit       :limit
    ?asc?        :ascending
    ?atts?       :attachments
    ?format      :format
    ?from        :from ; Not a stmt res param; added by lrsql for pagination
    :as          params}]
  (let [?stmt-id    (when ?stmt-id (u/str->uuid ?stmt-id))
        ?vstmt-id   (when ?vstmt-id (u/str->uuid ?vstmt-id))
        ?actor-ifi  (when ?actor (ua/actor->ifi ?actor))
        ?reg        (when ?reg (u/str->uuid ?reg))
        ?from       (when ?from (u/str->uuid ?from))
        ?since      (when ?since (u/str->time ?since))
        ?until      (when ?until (u/str->time ?until))
        rel-actors? (boolean ?rel-actors?) 
        rel-activs? (boolean ?rel-activs?)
        limit       (us/ensure-default-max-limit ?limit)
        asc?        (boolean ?asc?)
        format      (if ?format (keyword ?format) :exact)
        atts?       (boolean ?atts?)
        comm-params {:format       format
                     :attachments? atts?}]
    (if-some [stmt-id (or ?stmt-id ?vstmt-id)]
      ;; Single statement query
      (merge comm-params
             {:statement-id stmt-id
              :voided?      (boolean ?vstmt-id)})
      ;; Multiple statement query
      (cond-> comm-params
        true       (assoc :ascending?   asc?
                          :limit        limit
                          :query-params params)
        ?actor-ifi (assoc :actor-ifi       ?actor-ifi
                          :related-actors? rel-actors?)
        ?activ-iri (assoc :activity-iri        ?activ-iri
                          :related-activities? rel-activs?)
        ?verb-iri  (assoc :verb-iri ?verb-iri)
        ?reg       (assoc :registration ?reg)
        ?since     (assoc :since ?since)
        ?until     (assoc :until ?until)
        ?from      (assoc :from ?from)))))
