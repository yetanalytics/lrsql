(ns lrsql.hugsql.input
  "Functions to create HugSql inputs."
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [clj-uuid :as uuid]
            [java-time :as jt]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec :as hs]
            [lrsql.hugsql.functions :as f]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- current-time
  "Return the current time as a java.util.Instant object."
  []
  (jt/instant))

(defn- generate-uuid
  "Return a new sequential UUID."
  []
  (uuid/squuid))

(defn- parse-uuid
  "Parse a string as an UUID."
  [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn- parse-time
  "Parse a string as a java.util.Instant timestamp."
  [time-str]
  (jt/instant time-str))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Agent/Activity/Attachment Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-ifi
  "Returns a map between the IFI type and the IFI of `agent`."
  [agent]
  (not-empty (select-keys agent ["mbox"
                                 "mbox_sha1sum"
                                 "openid"
                                 "account"])))

(s/fdef agent->insert-input
  :args (s/cat :agent (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret (s/nilable hs/agent-insert-spec))

(defn agent->insert-input
  [agent]
  (when-some [ifi-m (get-ifi agent)]
    {:table             :agent
     :primary-key       (generate-uuid)
     :?name             (get agent "name")
     :ifi               ifi-m
     :identified-group? (= "Group" (get agent "objectType"))}))

(s/fdef activity->insert-input
  :args (s/cat :activity ::xs/activity)
  :ret hs/activity-insert-spec)

(defn activity->insert-input
  [activity]
  {:table          :activity
   :primary-key    (generate-uuid)
   :activity-iri   (get activity "id")
   :payload        activity})

(s/fdef attachment->insert-input
  :args (s/cat :attachment ::ss/attachment)
  :ret hs/attachment-insert-spec)

(defn attachment->insert-input
  [{content      :content
    content-type :contentType
    sha2         :sha2}]
  {:table          :attachment
   :primary-key    (generate-uuid)
   :attachment-sha sha2
   :content-type   content-type
   :file-url       "" ; TODO
   :payload        content})

(s/fdef agent-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :agent-usage :lrsql.hugsql.spec.agent/usage
               :agent-input hs/agent-insert-spec)
  :ret hs/statement-to-agent-insert-spec)

(defn agent-input->link-input
  [statement-id agent-usage {agent-ifi :ifi}]
  {:table        :statement-to-agent
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        agent-usage
   :ifi          agent-ifi})

(s/fdef activity-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :activity-usage :lrsql.hugsql.spec.activity/usage
               :activity-input hs/activity-insert-spec)
  :ret hs/statement-to-activity-insert-spec)

(defn- activity-input->link-input
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

(s/fdef attachment-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :attachment-input hs/attachment-insert-spec)
  :ret hs/statement-to-attachment-insert-spec)

(defn attachment-input->link-input
  [statement-id {attachment-id :attachment-sha}]
  {:table          :statement-to-attachment
   :primary-key    (generate-uuid)
   :statement-id   statement-id
   :attachment-sha attachment-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef statement->insert-input
  :args (s/cat :statement ::xs/statement)
  :ret (s/cat :statement hs/inputs-seq-spec
              :sub-statement (s/? hs/inputs-seq-spec)))

(defn statement->insert-input
  [statement]
  (let [;; Statement Properties
        {stmt-act  "actor"
         stmt-vrb  "verb"
         stmt-obj  "object"
         stmt-ctx  "context"
         stmt-auth "authority"}
        statement
        {stmt-obj-typ "objectType"}
        stmt-obj
        {stmt-ctx-acts "contextActivities"
         stmt-inst     "instructor"
         stmt-team     "team"}
        stmt-ctx
        {cat-acts "category"
         grp-acts "grouping"
         prt-acts "parent"
         oth-acts "other"}
        stmt-ctx-acts
        ;; Statement Revised Properties
        stmt-pk      (generate-uuid)
        stmt-id      (if-some [id (get statement "id")]
                       (parse-uuid id)
                       stmt-pk)
        stmt-time    (if-some [ts (get statement "timestamp")]
                       (parse-time ts)
                       (current-time))
        stmt-stored  (current-time)
        stmt-reg     (when-some [reg (get stmt-ctx "registration")]
                       (parse-uuid reg))
        stmt-ref-id  (when (= "StatementRef" stmt-obj-typ)
                       (parse-uuid (get stmt-obj "id")))
        stmt-vrb-id  (get stmt-vrb "id")
        voided?      (= "http://adlnet.gov/expapi/verbs/voided" stmt-vrb-id)
        ;; Sub Statement
        sub-stmt-id  (when (= "SubStatement" stmt-obj-typ)
                       (generate-uuid))
        sub-stmt     (when sub-stmt-id
                       (-> stmt-obj
                           (dissoc "objectType")
                           (assoc "id" (str sub-stmt-id))))
        ;; Statement Agents
        obj-agnt-in  (when (#{"Agent" "Group"} stmt-obj-typ)
                       (agent->insert-input stmt-obj))
        actr-agnt-in (agent->insert-input stmt-act)
        auth-agnt-in (agent->insert-input stmt-auth)
        inst-agnt-in (agent->insert-input stmt-inst)
        team-agnt-in (agent->insert-input stmt-team)
        ;; Statement Activities
        obj-act-in   (when (= "Activity" stmt-obj-typ)
                       (activity->insert-input stmt-obj))
        cat-acts-in  (when cat-acts (map activity->insert-input cat-acts))
        grp-acts-in  (when grp-acts (map activity->insert-input grp-acts))
        prt-acts-in  (when prt-acts (map activity->insert-input prt-acts))
        oth-acts-in  (when oth-acts (map activity->insert-input oth-acts))
        ;; Statement HugSql input
        stmt-input   {:table             :statement
                      :primary-key       stmt-pk
                      :statement-id      stmt-id
                      :?statement-ref-id stmt-ref-id
                      :timestamp         stmt-time
                      :stored            stmt-stored
                      :?registration     stmt-reg
                      :verb-iri          stmt-vrb-id
                      :voided?           voided?
                      :?sub-statement-id sub-stmt-id
                      :payload           statement}
        ;; Agent HugSql input
        agnt-inputs  (cond-> []
                       actr-agnt-in (conj actr-agnt-in)
                       obj-agnt-in  (conj obj-agnt-in)
                       auth-agnt-in (conj auth-agnt-in)
                       inst-agnt-in (conj inst-agnt-in)
                       team-agnt-in (conj team-agnt-in))
        ;; Activity HugSql input
        act-inputs   (cond-> []
                       obj-act-in  (conj obj-act-in)
                       cat-acts-in (concat cat-acts-in)
                       grp-acts-in (concat grp-acts-in)
                       prt-acts-in (concat prt-acts-in)
                       oth-acts-in (concat oth-acts-in))
        ;; Statement-to-Agent HugSql input
        agent->link  (partial agent-input->link-input stmt-id)
        stmt-agnts   (cond-> []
                       actr-agnt-in
                       (conj (agent->link "Actor" actr-agnt-in))
                       obj-agnt-in
                       (conj (agent->link "Object" obj-agnt-in))
                       auth-agnt-in
                       (conj (agent->link "Authority" auth-agnt-in))
                       inst-agnt-in
                       (conj (agent->link "Instructor" inst-agnt-in))
                       team-agnt-in
                       (conj (agent->link "Team" team-agnt-in)))
        ;; Statement-to-Activity HugSql input
        act->link    (partial activity-input->link-input stmt-id)
        stmt-acts    (cond-> []
                       obj-act-in
                       (conj (act->link "Object" obj-act-in))
                       cat-acts-in
                       (concat (map (partial act->link "Category") cat-acts-in))
                       grp-acts-in
                       (concat (map (partial act->link "Grouping") grp-acts-in))
                       prt-acts-in
                       (concat (map (partial act->link "Parent") prt-acts-in))
                       oth-acts-in
                       (concat (map (partial act->link "Other") oth-acts-in)))]
    (concat [stmt-input]
            agnt-inputs
            act-inputs
            stmt-agnts
            stmt-acts
            (when sub-stmt (statement->insert-input sub-stmt)))))

(s/fdef statements->insert-input
  :args (s/cat :statements (s/coll-of ::xs/statement :min-count 1 :gen-max 5))
  :ret (s/+ hs/inputs-seq-spec))

(defn statements->insert-input
  [statements]
  (mapcat statement->insert-input statements))

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
     :primary-key   (generate-uuid)
     :state-id      state-id
     :activity-id   activity-id
     :agent-id      (get-ifi (json/read-str agent))
     :?registration (when registration (parse-uuid registration))
     :last-modified (current-time)
     :document      document}

    ;; Agent Profile Document
    (and profile-id agent)
    {:table         :agent-profile-document
     :primary-key   (generate-uuid)
     :profile-id    profile-id
     :agent-id      (get-ifi (json/read-str agent))
     :last-modified (current-time)
     :document      document}

    ;; Activity Profile Document
    (and profile-id activity-id)
    {:table         :activity-profile-document
     :primary-key   (generate-uuid)
     :profile-id    profile-id
     :activity-id   activity-id
     :last-modified (current-time)
     :document      document}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: format
;; TODO: attachments

(s/fdef params->query-input
  :args (s/cat :params :xapi.statements.GET.request/params)
  :ret hs/statement-query-spec)

(defn params->query-input
  [{statement-id        :statementId
    voided-statement-id :voidedStatementId
    verb                :verb
    agent               :agent
    activity            :activity
    registration        :registration
    related-activities? :related_activities
    related-agents?     :related_agents
    since               :since
    until               :until
    limit               :limit
    ;; attachments?        :attachments
    ;; ascending?          :ascending
    ;; page                :page
    ;; from                :from
    }]
  (let [stmt-id   (when statement-id (parse-uuid statement-id))
        vstmt-id  (when voided-statement-id (parse-uuid voided-statement-id))
        reg       (when registration (parse-uuid registration))
        agent-ifi (when agent (get-ifi (json/read-str agent)))
        since     (when since (parse-time since))
        until     (when until (parse-time until))
        limit     (when limit (Long/parseLong limit))]
    (cond-> {}
      stmt-id
      (merge {:statement-id-snip (f/statement-id-snip {:statement-id stmt-id})
              :is-voided-snip    (f/is-voided-snip {:voided? false})})
      vstmt-id
      (merge {:statement-id-snip (f/statement-id-snip {:statement-id vstmt-id})
              :is-voided-snip    (f/is-voided-snip {:voided? true})})
      verb
      (assoc :verb-iri-snip
             (f/verb-iri-snip {:verb-iri verb}))
      reg
      (assoc :registration-snip
             (f/registration-snip {:registration reg}))
      since
      (assoc :timestamp-since-snip
             (f/timestamp-since-snip {:since since}))
      until
      (assoc :timestamp-until-snip
             (f/timestamp-until-snip {:until until}))
      agent-ifi
      (assoc :statement-to-agent-join-snip
             (f/statement-to-agent-join-snip
              {:agent-ifi              agent-ifi
               :actor-agent-usage-snip (when-not related-agents?
                                         (f/actor-agent-usage-snip))}))
      activity
      (assoc :statement-to-activity-join-snip
             (f/statement-to-activity-join-snip
              {:activity-iri               activity
               :object-activity-usage-snip (when-not related-activities?
                                             (f/object-activity-usage-snip))}))
      limit
      (assoc :limit-snip
             (f/limit-snip {:limit limit})))))
