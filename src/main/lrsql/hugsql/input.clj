(ns lrsql.hugsql.input
  "Functions to create HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec :as hs]
            [lrsql.hugsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; STATEMENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def voiding-verb "http://adlnet.gov/expapi/verbs/voided")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Agent/Activity Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef agent-insert-input
  :args (s/cat :agent (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable hs/agent-insert-spec))

(defn agent-insert-input
  "Given `agent`, construct the input for `functions/insert-agent!`."
  [agent]
  (when-some [ifi-str (u/agent->ifi agent)]
    {:table             :agent
     :primary-key       (u/generate-uuid)
     :agent-ifi         ifi-str
     :payload           (json/write-str agent)
     :identified-group? (= "Group" (get agent "objectType"))}))

(s/fdef activity-insert-input
  :args (s/cat :activity ::xs/activity)
  :ret hs/activity-insert-spec)

(defn activity-insert-input
  "Given `activity`, construct the input for `functions/insert-activity!`."
  [activity]
  {:table          :activity
   :primary-key    (u/generate-uuid)
   :activity-iri   (get activity "id")
   :payload        (json/write-str activity)})

(s/fdef statement-to-agent-insert-input
  :args (s/cat :statement-id ::hs/statement-id
               :agent-usage :lrsql.hugsql.spec.agent/usage
               :agent-input hs/agent-insert-spec)
  :ret hs/statement-to-agent-insert-spec)

(defn statement-to-agent-insert-input
  "Given `statement-id`, `agent-usage` and the input params map `insert-agent`,
   return the input for `functions/insert-statement-to-agent!`."
  [statement-id agent-usage {agent-ifi :agent-ifi}]
  {:table        :statement-to-agent
   :primary-key  (u/generate-uuid)
   :statement-id statement-id
   :usage        agent-usage
   :agent-ifi    agent-ifi})

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

(defn- statement-agent-insert-inputs
  "Helper to construct the `functions/insert-agent!` inputs for a statement's
   agents."
  [stmt-id stmt-act stmt-obj stmt-auth stmt-inst stmt-team sql-enums]
  (let [;; HugSql Enums
        {:keys [act-enum obj-enum auth-enum inst-enum team-enum]}
        sql-enums
        ;; Statement Agents
        stmt-obj     (when (#{"Agent" "Group"} (get stmt-obj "objectType"))
                       stmt-obj)
        act-agnt-in  (when stmt-act (agent-insert-input stmt-act))
        obj-agnt-in  (when stmt-obj (agent-insert-input stmt-obj))
        auth-agnt-in (when stmt-auth (agent-insert-input stmt-auth))
        inst-agnt-in (when stmt-inst (agent-insert-input stmt-inst))
        team-agnt-in (when stmt-team (agent-insert-input stmt-team))
        ;; Agent Inputs
        agnt-inputs  (cond-> []
                       act-agnt-in (conj act-agnt-in)
                       obj-agnt-in  (conj obj-agnt-in)
                       auth-agnt-in (conj auth-agnt-in)
                       inst-agnt-in (conj inst-agnt-in)
                       team-agnt-in (conj team-agnt-in))
        ;; Statement to Agent Inputs
        agent->link  (partial statement-to-agent-insert-input stmt-id)
        stmt-agnts   (cond-> []
                       act-agnt-in
                       (conj (agent->link act-enum act-agnt-in))
                       obj-agnt-in
                       (conj (agent->link obj-enum obj-agnt-in))
                       auth-agnt-in
                       (conj (agent->link auth-enum auth-agnt-in))
                       inst-agnt-in
                       (conj (agent->link inst-enum inst-agnt-in))
                       team-agnt-in
                       (conj (agent->link team-enum team-agnt-in)))]
    [agnt-inputs stmt-agnts]))

(defn- statement-activity-insert-inputs
  "Helper to construct the `functions/insert-activity!` inputs for a statement's
   activities."
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
        ;; Statement to Agent Enums
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
        ;; Agent Inputs
        [agnt-inputs stmt-agnt-inputs]
        (statement-agent-insert-inputs stmt-id
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
        [act-inputs stmt-act-inputs]
        (statement-activity-insert-inputs stmt-id
                                    sub-stmt-obj
                                    sub-stmt-ctx-acts
                                    {:obj-enum "SubObject"
                                     :cat-enum "SubCategory"
                                     :grp-enum "SubGrouping"
                                     :prt-enum "SubParent"
                                     :oth-enum "SubOther"})]
    [agnt-inputs act-inputs stmt-agnt-inputs stmt-act-inputs]))

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
                     :payload           (json/write-str statement)}
        ;; Agent HugSql Inputs
        [agnt-inputs stmt-agnt-inputs]
        (statement-agent-insert-inputs stmt-id
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
        [act-inputs stmt-act-inputs]
        (statement-activity-insert-inputs stmt-id
                                    stmt-obj
                                    stmt-ctx-acts
                                    {:obj-enum "Object"
                                     :cat-enum "Category"
                                     :grp-enum "Grouping"
                                     :prt-enum "Parent"
                                     :oth-enum "Other"})
        ;; SubStatement HugSql Inputs
        [sagnt-inputs sact-inputs sstmt-agnt-inputs sstmt-act-inputs]
        (when (= "SubStatement" stmt-obj-typ)
          (sub-statement-insert-inputs stmt-id stmt-obj))]
    (concat [stmt-input]
            agnt-inputs
            sagnt-inputs
            act-inputs
            sact-inputs
            stmt-agnt-inputs
            sstmt-agnt-inputs
            stmt-act-inputs
            sstmt-act-inputs)))

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
  :args (s/cat :params :xapi.statements.GET.request/params)
  :ret hs/statement-query-spec)

(defn statement-query-input
  "Construct the input for `command/query-statement!`."
  [{stmt-id     :statementId
    vstmt-id    :voidedStatementId
    verb-iri    :verb
    agent       :agent
    act-iri     :activity
    reg         :registration
    rel-acts?   :related_activities
    rel-agents? :related_agents
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
        rel-agents? (boolean rel-agents?)
        rel-acts?   (boolean rel-acts?)
        agent-ifi   (when agent (u/agent-str->ifi agent))
        limit       (when limit ; "0" = no limit
                      (let [n (Long/parseLong limit)]
                        (when (not (zero? n)) n)))]
    (cond-> {}
      stmt-id   (merge {:statement-id stmt-id :voided? false})
      vstmt-id  (merge {:statement-id vstmt-id :voided? true})
      verb-iri  (assoc :verb-iri verb-iri)
      reg       (assoc :registration reg)
      since     (assoc :since since)
      until     (assoc :until until)
      agent-ifi (merge {:agent-ifi agent-ifi :related-agents? rel-agents?})
      act-iri   (merge {:activity-iri act-iri :related-activities? rel-acts?})
      limit     (assoc :limit limit)
      asc?      (assoc :ascending asc?)
      atts?     (assoc :attachments? atts?))))

(s/fdef agent-query-input
  :args (s/cat :params hs/agent-params-spec)
  :ret hs/agent-query-spec)

(defn agent-query-input
  "Construct an input for `command/query-agent!`"
  [{agent :agent}]
  {:agent-ifi (u/agent-str->ifi agent)})

(s/fdef activity-query-input
  :args (s/cat :params hs/activity-params-spec)
  :ret hs/activity-query-spec)

(defn activity-query-input
  "Construct an input for `command/query-input!`"
  [{activity-id :activityId}]
  {:activity-iri activity-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; DOCUMENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- state-document-basics
  "Common properties for state document inputs. `state-id?` controls whether
   the state ID property is added (true for singleton queries, false for
   array-valued queries)."
  [{state-id     :stateId
    activity-id  :activityId
    agent        :agent
    registration :registration}
   state-id?]
  (cond-> {:table         :state-document
           :activity-iri  activity-id
           :agent-ifi     (u/agent-str->ifi agent)
           :?registration (when registration (u/str->uuid registration))}
    state-id?
    (assoc :state-id state-id)))

(defn- agent-profile-document-basics
  "Common properties for agent profile document inputs. `profile-id?` controls
   whether the profile ID property is added (true for singleton queries, false
   for array-valued queries)."
  [{profile-id :profileId
    agent      :agent}
   profile-id?]
  (cond-> {:table     :agent-profile-document
           :agent-ifi (u/agent-str->ifi agent)}
    profile-id?
    (assoc :profile-id profile-id)))

(defn- activity-profile-document-basics
  "Common properties for activity profile document inputs. `profile-id?`
   controls whether the profile ID property is added (true for singleton
   queries, false for array-valued queries)."
  [{profile-id  :profileId
    activity-id :activityId}
   profile-id?]
  (cond-> {:table        :activity-profile-document
           :activity-iri activity-id}
    profile-id?
    (assoc :profile-id profile-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- document-insert-basics
  "Common properties for insertion inputs, including the primary key, the last
   modified time, and `document`"
  [document]
  {:primary-key   (u/generate-uuid)
   :last-modified (u/current-time)
   :document      document})

(s/fdef document-insert-input
  :args (s/cat :id-params hs/id-params-spec :document bytes?)
  :ret hs/document-insert-spec
  :fn (fn [{:keys [args ret]}]
        (= (u/document-dispatch (:id-params args)) (:table ret))))

(defmulti document-insert-input
  "Given `id-params` and `document`, construct the input for
   `command/insert-document!` and `command/update-document!`"
  {:arglists '([id-params document])}
  (fn [id-params _] (u/document-dispatch id-params)))

(defmethod document-insert-input :state-document
  [id-params document]
  (merge (state-document-basics id-params true)
         (document-insert-basics document)))

(defmethod document-insert-input :agent-profile-document
  [id-params document]
  (merge (agent-profile-document-basics id-params true)
         (document-insert-basics document)))

(defmethod document-insert-input :activity-profile-document
  [id-params document]
  (merge (activity-profile-document-basics id-params true)
         (document-insert-basics document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Query + Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Single document query/deletion

(s/fdef document-input
  :args (s/cat :id-params hs/id-params-spec)
  :ret hs/document-input-spec
  :fn (fn [{:keys [args ret]}]
        (= (u/document-dispatch (:id-params args)) (:table ret))))

(defmulti document-input
  "Given `id-params`, construct the input for `command/query-document` and
   `command/delete-document!`"
  {:arglists '([id-params])}
  u/document-dispatch)

(defmethod document-input :state-document
  [id-params]
  (state-document-basics id-params true))

(defmethod document-input :agent-profile-document
  [id-params]
  (agent-profile-document-basics id-params true))

(defmethod document-input :activity-profile-document
  [id-params]
  (activity-profile-document-basics id-params true))

;; Multiple document deletion
;; Multi-delete is only supported for state docs, thus no need for multimethod

(s/fdef document-multi-input
  :args (s/cat :id-params :xapi.document.state/id-params)
  :ret hs/state-doc-multi-input-spec)

(defn document-multi-input
  "Given params, construct the input for `command/delete-document!` in the
   case of multiple documents."
  [id-params]
  (state-document-basics id-params false))

;; Multiple document ID query

(defn- add-since-to-map
  "Add the `:since` property to `m` if `:since` is present/not nil."
  [{since :since} m]
  (cond-> m
    since
    (assoc :since (u/str->time since))))

(s/fdef document-ids-input
  :args (s/cat :query-params hs/query-params-spec)
  :ret hs/document-ids-query-spec
  :fn (fn [{:keys [args ret]}]
        (= (u/document-dispatch (:query-params args)) (:table ret))))

(defmulti document-ids-input
  "Given `query-params`, return the input for `command/query-document-ids`."
  {:arglist '([query-params])}
  u/document-dispatch)

(defmethod document-ids-input :state-document
  [query-params]
  (->> (state-document-basics query-params false)
       (add-since-to-map query-params)))

(defmethod document-ids-input :agent-profile-document
  [query-params]
  (->> (agent-profile-document-basics query-params false)
       (add-since-to-map query-params)))

(defmethod document-ids-input :activity-profile-document
  [query-params]
  (->> (activity-profile-document-basics query-params false)
       (add-since-to-map query-params)))
