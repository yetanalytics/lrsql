(ns lrsql.hugsql.input
  "Functions to create HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec :as hs]
            [lrsql.hugsql.util :as u]))

(def voiding-verb "http://adlnet.gov/expapi/verbs/voided")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Agent/Activity/Attachment Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-ifi
  "Returns a map between the IFI type and the IFI of `agent`.
   Returns nil if the agent doesn't have an IFI (e.g. Anonymous Group)."
  [agnt]
  (let [agnt' (not-empty (select-keys agnt ["mbox"
                                            "mbox_sha1sum"
                                            "openid"
                                            "account"]))]
    ;; Need to order `name` and `homepage` properties.
    ;; Important when comparing JSON string/bytes.
    (cond-> agnt'
      (contains? agnt' "account")
      (update "account" (partial into (sorted-map))))))

(s/fdef agent->insert-input
  :args (s/cat :agent (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable hs/agent-insert-spec))

(defn agent->insert-input
  "Given a Statement Agent, return the HugSql params map for `insert-agent`."
  [agent]
  (when-some [ifi-m (get-ifi agent)]
    {:table             :agent
     :primary-key       (u/generate-uuid)
     :?name             (get agent "name")
     :agent-ifi         (json/write-str ifi-m)
     :identified-group? (= "Group" (get agent "objectType"))}))

(s/fdef activity->insert-input
  :args (s/cat :activity ::xs/activity)
  :ret hs/activity-insert-spec)

(defn activity->insert-input
  "Given a Statement Agent, return the HugSql params map for `insert-activity`."
  [activity]
  {:table          :activity
   :primary-key    (u/generate-uuid)
   :activity-iri   (get activity "id")
   :payload        (json/write-str activity)})

(s/fdef attachment->insert-input
  :args (s/cat :attachment ::ss/attachment)
  :ret hs/attachment-insert-spec)

(defn attachment->insert-input
  "Given Attachment data, return the HugSql params map for `insert-attachment`."
  [{content      :content
    content-type :contentType
    length       :length
    sha2         :sha2}]
  {:table          :attachment
   :primary-key    (u/generate-uuid)
   :attachment-sha sha2
   :content-type   content-type
   :content-length length
   :file-url       "" ; TODO
   :payload        content})

(s/fdef agent-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :agent-usage :lrsql.hugsql.spec.agent/usage
               :agent-input hs/agent-insert-spec)
  :ret hs/statement-to-agent-insert-spec)

(defn agent-input->link-input
  "Given `statement-id`, `agent-usage` and the HugSql params map for
   `insert-agent`, return the HugSql params map for
   `insert-statement-to-agent`."
  [statement-id agent-usage {agent-ifi :agent-ifi}]
  {:table        :statement-to-agent
   :primary-key  (u/generate-uuid)
   :statement-id statement-id
   :usage        agent-usage
   :agent-ifi    agent-ifi})

(s/fdef activity-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :activity-usage :lrsql.hugsql.spec.activity/usage
               :activity-input hs/activity-insert-spec)
  :ret hs/statement-to-activity-insert-spec)

(defn activity-input->link-input
  "Given `statement-id`, `activity-usage` and the HugSql params map for
   `insert-activity`, return the HugSql params map for
   `insert-statement-to-activity`."
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (u/generate-uuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

(s/fdef attachment-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :attachment-input hs/attachment-insert-spec)
  :ret hs/statement-to-attachment-insert-spec)

(defn attachment-input->link-input
  "Given `statement-id` and the HugSql params map for `insert-attachment`,
   return the HugSql params map for `insert-statement-to-attachment`."
  [statement-id {attachment-id :attachment-sha}]
  {:table          :statement-to-attachment
   :primary-key    (u/generate-uuid)
   :statement-id   statement-id
   :attachment-sha attachment-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- statement->agent-inputs
  [stmt-id stmt-act stmt-obj stmt-auth stmt-inst stmt-team sql-enums]
  (let [;; HugSql Enums
        {:keys [act-enum obj-enum auth-enum inst-enum team-enum]}
        sql-enums
        ;; Statement Agents
        stmt-obj     (when (#{"Agent" "Group"} (get stmt-obj "objectType"))
                       stmt-obj)
        act-agnt-in  (when stmt-act (agent->insert-input stmt-act))
        obj-agnt-in  (when stmt-obj (agent->insert-input stmt-obj))
        auth-agnt-in (when stmt-auth (agent->insert-input stmt-auth))
        inst-agnt-in (when stmt-inst (agent->insert-input stmt-inst))
        team-agnt-in (when stmt-team (agent->insert-input stmt-team))
        ;; Agent Inputs
        agnt-inputs  (cond-> []
                       act-agnt-in (conj act-agnt-in)
                       obj-agnt-in  (conj obj-agnt-in)
                       auth-agnt-in (conj auth-agnt-in)
                       inst-agnt-in (conj inst-agnt-in)
                       team-agnt-in (conj team-agnt-in))
        ;; Statement to Agent Inputs
        agent->link' (partial agent-input->link-input stmt-id)
        stmt-agnts   (cond-> []
                       act-agnt-in
                       (conj (agent->link' act-enum act-agnt-in))
                       obj-agnt-in
                       (conj (agent->link' obj-enum obj-agnt-in))
                       auth-agnt-in
                       (conj (agent->link' auth-enum auth-agnt-in))
                       inst-agnt-in
                       (conj (agent->link' inst-enum inst-agnt-in))
                       team-agnt-in
                       (conj (agent->link' team-enum team-agnt-in)))]
    [agnt-inputs stmt-agnts]))

(defn- statement->activity-inputs
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
        obj-act-in   (when stmt-obj (activity->insert-input stmt-obj))
        cat-acts-in  (when cat-acts (map activity->insert-input cat-acts))
        grp-acts-in  (when grp-acts (map activity->insert-input grp-acts))
        prt-acts-in  (when prt-acts (map activity->insert-input prt-acts))
        oth-acts-in  (when oth-acts (map activity->insert-input oth-acts))
        ;; Activity Inputs
        act-inputs   (cond-> []
                       obj-act-in  (conj obj-act-in)
                       cat-acts-in (concat cat-acts-in)
                       grp-acts-in (concat grp-acts-in)
                       prt-acts-in (concat prt-acts-in)
                       oth-acts-in (concat oth-acts-in))
        ;; Statement to Agent Enums
        act->link    (partial activity-input->link-input stmt-id)
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

(defn- sub-statement->insert-inputs
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
        (statement->agent-inputs stmt-id
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
        (statement->activity-inputs stmt-id
                                    sub-stmt-obj
                                    sub-stmt-ctx-acts
                                    {:obj-enum "SubObject"
                                     :cat-enum "SubCategory"
                                     :grp-enum "SubGrouping"
                                     :prt-enum "SubParent"
                                     :oth-enum "SubOther"})]
    [agnt-inputs act-inputs stmt-agnt-inputs stmt-act-inputs]))

(s/fdef statement->insert-inputs
  :args (s/cat :statement hs/prepared-statement-spec)
  :ret hs/statement-inputs-seq-spec)

(defn statement->insert-inputs
  "Given `statement`, return a seq of HugSql insertion function params maps,
   starting with the params for `insert-statement`."
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
        (statement->agent-inputs stmt-id
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
        (statement->activity-inputs stmt-id
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
          (sub-statement->insert-inputs stmt-id stmt-obj))]
    (concat [stmt-input]
            agnt-inputs
            sagnt-inputs
            act-inputs
            sact-inputs
            stmt-agnt-inputs
            sstmt-agnt-inputs
            stmt-act-inputs
            sstmt-act-inputs)))

(s/fdef statements->insert-inputs
  :args (s/cat :statements (s/coll-of hs/prepared-statement-spec
                                      :min-count 1
                                      :gen-max 5))
  :ret (s/+ hs/statement-inputs-seq-spec))

(defn statements->insert-inputs
  "Given a `statements` coll, return a seq of HugSql insertion fn param maps."
  [statements]
  (mapcat statement->insert-inputs statements))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attachment Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef attachments->insert-inputs
  :args hs/prepared-attachments-spec
  :ret hs/attachment-inputs-seq-spec)

(def invalid-attachment-emsg
  "Statement attachment does not have a fileUrl value nor a matching SHA2!")

(defn attachments->insert-inputs
  "Given colls `statements` and `attachments`, return a seq of HugSql insertion
   fn param maps."
  [statements attachments]
  (let [sha-input-m
        (reduce
         (fn [m {sha2 :sha2 :as att}]
           (assoc m sha2 (attachment->insert-input att)))
         {}
         attachments)
        get-att-input
        (fn [{sha2 "sha2" :as _stmt-att}]
          (sha-input-m sha2))
        reduce-att
        (fn [stmt-id acc att]
          (let [att-input (get-att-input att)]
            (cond
              ;; Attachment sha was found in `attachments`
              att-input
              (conj acc (attachment-input->link-input (u/str->uuid stmt-id)
                                                      att-input))
              ;; Attachment contains fileUrl
              ;; Skip url resource verification for simplicity
              (contains? att "fileUrl")
              acc
              ;; Otherwise throw error
              :else
              (throw (ex-info invalid-attachment-emsg
                              {:kind         ::invalid-attachment
                               :statement-id stmt-id
                               :attachmment  att})))))
        reduce-stmt-atts
        (fn [acc {stmt-id "id" stmt-atts "attachments"}]
          (reduce (partial reduce-att stmt-id) acc stmt-atts))
        link-inputs
        (reduce reduce-stmt-atts '() statements)]
    (concat (vals sha-input-m) link-inputs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Document Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef document->insert-input
  :args (s/cat
         :id-params
         (s/alt :state :xapi.document.state/id-params
                :agent-profile :xapi.document.agent-profile/id-params
                :activity-profile :xapi.document.activity-profile/id-params)
         :document any?) ; TODO: bytes? predicate
  :ret (s/or :state hs/state-document-insert-spec
             :agent-profile hs/agent-profile-document-insert-spec
             :activity-profile hs/activity-profile-document-insert-spec))

(defn document->insert-input
  "Given `id-params` and `document`, return the appropriate HugSql insertion
   function params map."
  [{state-id     :stateId
    profile-id   :profileId
    activity-id  :activityId
    agent        :agent
    registration :registration
    :as          _id-params}
   ^bytes document]
  (cond
    ;; State Document
    state-id
    {:table         :state-document
     :primary-key   (u/generate-uuid)
     :state-id      state-id
     :activity-iri  activity-id
     :agent-ifi     (json/write-str (get-ifi (json/read-str agent)))
     :?registration (when registration (u/str->uuid registration))
     :last-modified (u/current-time)
     :document      document}

    ;; Agent Profile Document
    (and profile-id agent)
    {:table         :agent-profile-document
     :primary-key   (u/generate-uuid)
     :profile-id    profile-id
     :agent-ifi     (json/write-str (get-ifi (json/read-str agent)))
     :last-modified (u/current-time)
     :document      document}

    ;; Activity Profile Document
    (and profile-id activity-id)
    {:table         :activity-profile-document
     :primary-key   (u/generate-uuid)
     :profile-id    profile-id
     :activity-iri  activity-id
     :last-modified (u/current-time)
     :document      document}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: format
;; TODO: attachments

(s/fdef params->query-input
  :args (s/cat :params :xapi.statements.GET.request/params)
  :ret hs/statement-query-spec)

(defn params->query-input ; TODO: Rename to statement-query-input
  "Given params, return the HugSql params map for `query-statement`."
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
        agent-ifi   (when agent
                      (some-> agent json/read-str get-ifi json/write-str))
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
