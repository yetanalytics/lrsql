(ns lrsql.hugsql.input.statement
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec.statement :as hs]
            [lrsql.hugsql.util :as u]
            [lrsql.hugsql.util.actor :as ua]))

(def voiding-verb "http://adlnet.gov/expapi/verbs/voided")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actor/Activity Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef actor-insert-input
  :args (s/cat :actor (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable hs/actor-insert-spec))

(defn actor-insert-input
  "Given `actor`, construct the input for `functions/insert-actor!`."
  [actor]
  (when-some [ifi-str (ua/actor->ifi actor)]
    {:table       :actor
     :primary-key (u/generate-uuid)
     :actor-ifi   ifi-str
     :actor-type  (get actor "objectType" "Agent")
     :payload     (u/write-json actor)}))

(s/fdef activity-insert-input
  :args (s/cat :activity ::xs/activity)
  :ret hs/activity-insert-spec)

(defn activity-insert-input
  "Given `activity`, construct the input for `functions/insert-activity!`."
  [activity]
  {:table        :activity
   :primary-key  (u/generate-uuid)
   :activity-iri (get activity "id")
   :payload      (u/write-json activity)})

(s/fdef statement-to-actor-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :actor-usage :lrsql.hugsql.spec.actor/usage
               :actor-input hs/actor-insert-spec)
  :ret hs/statement-to-actor-insert-spec)

(defn statement-to-actor-insert-input
  "Given `statement-id`, `actor-usage` and the input to `f/insert-actor!`,
   return the input for `functions/insert-statement-to-actor!`."
  [statement-id actor-usage {actor-ifi :actor-ifi}]
  {:table        :statement-to-actor
   :primary-key  (u/generate-uuid)
   :statement-id statement-id
   :usage        actor-usage
   :actor-ifi    actor-ifi})

(s/fdef statement-to-activity-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :activity-usage :lrsql.hugsql.spec.activity/usage
               :activity-input hs/activity-insert-spec)
  :ret hs/statement-to-activity-insert-spec)

(defn statement-to-activity-insert-input
  "Given `statement-id`, `activity-usage` and the HugSql params map for
   `insert-activity`, return the HugSql params map for
   `functions/insert-statement-to-activity!`."
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (u/generate-uuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- statement-actor-insert-inputs
  "Helper to construct the `functions/insert-actor!` inputs for a statement's
   Agents and Groups."
  [stmt-id stmt-act stmt-obj stmt-auth stmt-inst stmt-team sql-enums]
  (let [;; HugSql Enums
        {:keys [act-enum obj-enum auth-enum inst-enum team-enum]}
        sql-enums
        ;; Statement Actors
        stmt-obj     (when (#{"Agent" "Group"} (get stmt-obj "objectType"))
                       stmt-obj)
        act-input    (when stmt-act (actor-insert-input stmt-act))
        obj-input    (when stmt-obj (actor-insert-input stmt-obj))
        auth-input   (when stmt-auth (actor-insert-input stmt-auth))
        inst-input   (when stmt-inst (actor-insert-input stmt-inst))
        team-input   (when stmt-team (actor-insert-input stmt-team))
        ;; Actor Inputs
        actor-inputs (cond-> []
                       act-input (conj act-input)
                       obj-input  (conj obj-input)
                       auth-input (conj auth-input)
                       inst-input (conj inst-input)
                       team-input (conj team-input))
        ;; Statement to Actor Inputs
        actor->link  (partial statement-to-actor-insert-input stmt-id)
        stmt-actors  (cond-> []
                       act-input
                       (conj (actor->link act-enum act-input))
                       obj-input
                       (conj (actor->link obj-enum obj-input))
                       auth-input
                       (conj (actor->link auth-enum auth-input))
                       inst-input
                       (conj (actor->link inst-enum inst-input))
                       team-input
                       (conj (actor->link team-enum team-input)))]
    [actor-inputs stmt-actors]))

(defn- statement-activity-insert-inputs
  "Helper to construct the `functions/insert-activity!` inputs for a statement's
   Activities."
  [stmt-id stmt-obj stmt-ctx-acts sql-enums]
  (let [;; HugSql enums
        {:keys [obj-enum cat-enum grp-enum prt-enum oth-enum]}
        sql-enums
        ;; Statement Activities
        {cat-acts "category"
         grp-acts "grouping"
         prt-acts "parent"
         oth-acts "other"}
        stmt-ctx-acts
        stmt-obj     (when (#{"Activity"} (get stmt-obj "objectType"))
                       stmt-obj)
        obj-act-in   (when stmt-obj (activity-insert-input stmt-obj))
        cat-acts-in  (when cat-acts (map activity-insert-input cat-acts))
        grp-acts-in  (when grp-acts (map activity-insert-input grp-acts))
        prt-acts-in  (when prt-acts (map activity-insert-input prt-acts))
        oth-acts-in  (when oth-acts (map activity-insert-input oth-acts))
        ;; Activity Inputs
        act-inputs   (cond-> []
                       obj-act-in  (conj obj-act-in)
                       cat-acts-in (concat cat-acts-in)
                       grp-acts-in (concat grp-acts-in)
                       prt-acts-in (concat prt-acts-in)
                       oth-acts-in (concat oth-acts-in))
        ;; Statement to Activity Enums
        act->link    (partial statement-to-activity-insert-input stmt-id)
        stmt-acts    (cond-> []
                       obj-act-in
                       (conj (act->link obj-enum obj-act-in))
                       cat-acts-in
                       (concat (map (partial act->link cat-enum) cat-acts-in))
                       grp-acts-in
                       (concat (map (partial act->link grp-enum) grp-acts-in))
                       prt-acts-in
                       (concat (map (partial act->link prt-enum) prt-acts-in))
                       oth-acts-in
                       (concat (map (partial act->link oth-enum) oth-acts-in)))]
    [act-inputs stmt-acts]))

(defn- sub-statement-insert-inputs
  [stmt-id sub-statement]
  (let [;; SubStatement Properties
        {sub-stmt-act "actor"
         sub-stmt-obj "object"
         sub-stmt-ctx "context"}
        sub-statement
        {sub-stmt-ctx-acts "contextActivities"
         sub-stmt-inst     "instructor"
         sub-stmt-team     "team"}
        sub-stmt-ctx
        ;; Actor Inputs
        [actor-inputs stmt-actor-inputs]
        (statement-actor-insert-inputs stmt-id
                                       sub-stmt-act
                                       sub-stmt-obj
                                       nil ; No Authority for SubStatements
                                       sub-stmt-inst
                                       sub-stmt-team
                                       {:act-enum  "SubActor"
                                        :obj-enum  "SubObject"
                                        :inst-enum "SubInstructor"
                                        :team-enum "SubTeam"})
        ;; Activity Inputs
        [activity-inputs stmt-activity-inputs]
        (statement-activity-insert-inputs stmt-id
                                          sub-stmt-obj
                                          sub-stmt-ctx-acts
                                          {:obj-enum "SubObject"
                                           :cat-enum "SubCategory"
                                           :grp-enum "SubGrouping"
                                           :prt-enum "SubParent"
                                           :oth-enum "SubOther"})]
    [actor-inputs activity-inputs stmt-actor-inputs stmt-activity-inputs]))

(s/fdef statement-insert-inputs
  :args (s/cat :statement hs/prepared-statement-spec)
  :ret hs/statement-insert-seq-spec)

(defn statement-insert-inputs
  "Given `statement`, return a seq of inputs that serve as the input for
   `command/insert-statements!`, starting with the params for
   `functions/insert-statement!`."
  [statement]
  (let [;; Statement Properties
        ;; id, timestamp, stored, and authority should have already been
        ;; set by `prepare-statement`.
        {stmt-id   "id"
         stmt-time "timestamp"
         stmt-stor "stored"
         stmt-act  "actor"
         stmt-vrb  "verb"
         stmt-obj  "object"
         stmt-ctx  "context"
         stmt-auth "authority"}
        statement
        {stmt-obj-typ "objectType"}
        stmt-obj
        {stmt-ctx-acts "contextActivities"
         stmt-inst     "instructor"
         stmt-team     "team"
         stmt-reg      "registration"}
        stmt-ctx
        ;; Revised Properties
        stmt-pk     (u/generate-uuid)
        stmt-id     (u/str->uuid stmt-id)
        stmt-time   (u/str->time stmt-time)
        stmt-stored (u/str->time stmt-stor)
        stmt-reg    (when stmt-reg (u/str->uuid stmt-reg))
        stmt-ref-id (when (= "StatementRef" stmt-obj-typ)
                      (u/str->uuid (get stmt-obj "id")))
        stmt-vrb-id (get stmt-vrb "id")
        ;; `stmt-ref-id` should always be true here, but we still sanity check
        voiding?    (and (some? stmt-ref-id)
                         (= voiding-verb stmt-vrb-id))
        ;; Statement HugSql input
        stmt-input  {:table             :statement
                     :primary-key       stmt-pk
                     :statement-id      stmt-id
                     :?statement-ref-id stmt-ref-id
                     :timestamp         stmt-time
                     :stored            stmt-stored
                     :?registration     stmt-reg
                     :verb-iri          stmt-vrb-id
                     :voided?           false
                     :voiding?          voiding?
                     :payload           (u/write-json statement)}
        ;; Actor HugSql Inputs
        [actor-inputs stmt-actor-inputs]
        (statement-actor-insert-inputs stmt-id
                                       stmt-act
                                       stmt-obj
                                       stmt-auth
                                       stmt-inst
                                       stmt-team
                                       {:act-enum  "Actor"
                                        :obj-enum  "Object"
                                        :auth-enum "Authority"
                                        :inst-enum "Instructor"
                                        :team-enum "Team"})
        ;; Activity HugSql Inputs
        [activ-inputs stmt-activ-inputs]
        (statement-activity-insert-inputs stmt-id
                                          stmt-obj
                                          stmt-ctx-acts
                                          {:obj-enum "Object"
                                           :cat-enum "Category"
                                           :grp-enum "Grouping"
                                           :prt-enum "Parent"
                                           :oth-enum "Other"})
        ;; SubStatement HugSql Inputs
        [sactor-inputs sactiv-inputs sstmt-actor-inputs sstmt-activ-inputs]
        (when (= "SubStatement" stmt-obj-typ)
          (sub-statement-insert-inputs stmt-id stmt-obj))]
    (concat [stmt-input]
            actor-inputs
            sactor-inputs
            activ-inputs
            sactiv-inputs
            stmt-actor-inputs
            sstmt-actor-inputs
            stmt-activ-inputs
            sstmt-activ-inputs)))

(s/fdef statements-insert-inputs
  :args (s/cat :statements (s/coll-of hs/prepared-statement-spec
                                      :min-count 1
                                      :gen-max 5))
  :ret (s/+ hs/statement-insert-seq-spec))

(defn statements-insert-inputs
  "Given the coll `statements`, return a seq of input maps that serve as the
   input for `command/insert-statements!`"
  [statements]
  (mapcat statement-insert-inputs statements))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attachment Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef attachment-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :attachment ::ss/attachment)
  :ret hs/attachment-insert-spec)

(defn attachment-insert-input
  "Given `statement-id` and `attachment`, construct the input for
   `functions/insert-attachment!`. `statement-id` will be associated with
   `attachment` as a foreign key reference."
  [statement-id attachment]
  (let [{content      :content
         content-type :contentType
         length       :length
         sha2         :sha2}
        attachment]
    {:table          :attachment
     :primary-key    (u/generate-uuid)
     :statement-id   statement-id
     :attachment-sha sha2
     :content-type   content-type
     :content-length length
     :content        content}))

(s/fdef attachments-insert-inputs
  :args hs/prepared-attachments-spec
  :ret hs/attachment-insert-seq-spec)

(defn attachments-insert-inputs
  "Given colls `statements` and `attachments`, return a seq of
   `functions/insert-attachment!` inputs that will be part of the input for
   `command/insert-statements!`. Each attachment in `attachments` must have an
   associated attachment object in `statements` (including substatements)."
  [statements attachments]
  ;; NOTE: Assume that the LRS has already validated that every statement
  ;; attachment object has a fileUrl or valid SHA2 value.
  ;; NOTE: SHAs may collide, so we also equate on length and content type.
  (let [;; attachment-to-statement-id map
        att-stmt-id-m
        (reduce
         (fn [m {stmt-id "id" stmt-obj "object" stmt-atts "attachments"}]
           (let [stmt-atts' (cond-> stmt-atts
                              ;; SubStatement attachments
                              (= "SubStatement" (get stmt-obj "objectType"))
                              (concat (get stmt-obj "attachments")))]
             (reduce
              (fn [m' {:strs [sha2 length contentType] :as _att}]
                (assoc m' [sha2 length contentType] stmt-id))
              m
              stmt-atts')))
         {}
         statements)
        ;; attachment to statement id
        att->stmt-id
        (fn [{:keys [sha2 length contentType] :as _att}]
          (att-stmt-id-m [sha2 length contentType]))]
    (reduce
     (fn [acc attachment]
       (if-some [stmt-id (att->stmt-id attachment)]
         (conj acc (attachment-insert-input (u/str->uuid stmt-id)
                                            attachment))
         (throw (ex-info "Attachment is not associated with a Statement in request."
                         {:kind ::invalid-attachment
                          :attachment attachment
                          :statements statements}))))
     '()
     attachments)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: format

(s/fdef statement-query-input
  :args (s/cat :params hs/get-statements-params)
  :ret hs/statement-query-spec)

(defn statement-query-input
  "Construct the input for `command/query-statement!`."
  [{stmt-id     :statementId
    vstmt-id    :voidedStatementId
    verb-iri    :verb
    actor       :agent
    act-iri     :activity
    reg         :registration
    rel-activs? :related_activities
    rel-actors? :related_agents
    since       :since
    until       :until
    limit       :limit
    asc?        :ascending
    atts?       :attachments
    ;; page                :page
    ;; from                :from
    }]
  (let [stmt-id     (when stmt-id (u/str->uuid stmt-id))
        vstmt-id    (when vstmt-id (u/str->uuid vstmt-id))
        reg         (when reg (u/str->uuid reg))
        since       (when since (u/str->time since))
        until       (when until (u/str->time until))
        rel-actors? (boolean rel-actors?)
        rel-activs? (boolean rel-activs?)
        actor-ifi   (when actor (ua/actor->ifi actor))
        limit       (when (and (int? limit) (not (zero? limit)))
                      limit)] ; "0" = no limit
    (cond-> {}
      stmt-id   (merge {:statement-id stmt-id :voided? false})
      vstmt-id  (merge {:statement-id vstmt-id :voided? true})
      verb-iri  (assoc :verb-iri verb-iri)
      reg       (assoc :registration reg)
      since     (assoc :since since)
      until     (assoc :until until)
      actor-ifi (merge {:actor-ifi actor-ifi :related-actors? rel-actors?})
      act-iri   (merge {:activity-iri act-iri :related-activities? rel-activs?})
      limit     (assoc :limit limit)
      asc?      (assoc :ascending asc?)
      atts?     (assoc :attachments? atts?))))
