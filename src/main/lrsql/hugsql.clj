(ns lrsql.hugsql
  (:require [clojure.spec.alpha :as s]
            [clj-uuid :as uuid]
            [java-time :as jt]
            [hugsql.core :as hugsql]
            [lrsql.hugsql.spec :as hs]))

(hugsql/def-db-fns "db/h2/h2_insert.sql")
(hugsql/def-db-fns "db/h2/h2_query.sql")

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Store
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; /* Need explicit properties for querying Agents Resource */
;; Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - Name: STRING
;; - Mbox: STRING
;; - MboxSHA1Sum: STRING
;; - OpenID: STRING
;; - AccountName: STRING
;; - AccountHomepage: STRING
;; - IsIdentifiedGroup: BOOLEAN NOT NULL DEFAULT FALSE -- Treat Identified Groups as Agents

(defn- get-ifi
  "Return the Inverse Functional Identifier of `obj` if it is an Actor or an
   Identified Group. Return value is a map between the IFI type and the IFI."
  [obj]
  (when (#{"Agent" "Group"} (get obj "objectType"))
    (let [mbox         (get obj "mbox")
          mbox-sha1sum (get obj "mbox_sha1sum")
          openid       (get obj "openid")
          account      (get obj "account")]
      (cond
        mbox         {:mbox mbox}
        mbox-sha1sum {:mbox-sha1sum mbox-sha1sum}
        openid       {:openid openid}
        account      {:account {:name     (get account "name")
                                :homepage (get account "homepage")}}
        :else        nil))))

(defn agent->input
  [agent]
  (when-some [ifi-m (get-ifi agent)]
    (cond-> {:table       :agent
             :primary-key (generate-uuid)
             :ifi         ifi-m}
      (contains? agent "name")
      (assoc :name (get agent "name"))
      (= "Group" (get agent "objectType"))
      (assoc :identified-group? true))))

;; Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ActivityIRI: STRING UNIQUE KEY NOT NULL
;; - Data: JSON NOT NULL

(defn activity->input
  [activity]
  {:table          :activity
   :primary-key    (generate-uuid)
   :activity-iri   (get activity "id")
   :payload        activity})

;; Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - SHA2: STRING UNIQUE KEY NOT NULL
;; - ContentType: STRING NOT NULL
;; - FileURL: STRING NOT NULL -- Either an external URL or the URL to a LRS location
;; - Data: BINARY NOT NULL

(defn attachment->input
  [{content      :content
    content-type :contentType
    sha2         :sha2}]
  {:table          :attachment
   :primary-key    (generate-uuid)
   :attachment-sha sha2
   :content-type   content-type
   :file-url       "" ; TODO
   :payload        content})

;; Statement-to-Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL
;; - AgentKey: UUID NOT NULL
;; - AgentIFI: STRING NOT NULL
;; - AgentIFIType: STRING IN ('Mbox', 'MboxSHA1Sum', 'OpenID', 'Account') NOT NULL

(defn- agent-input->link-input
  [statement-id agent-usage {agent-ifi :ifi :as _agent}]
  {:table        :statement-to-agent
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        agent-usage
   :ifi          agent-ifi})

;; Statement-to-Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Object', 'Category', 'Grouping', 'Parent', 'Other') NOT NULL
;; - ActivityKey: UUID NOT NULL
;; - ActivityIRI: STRING NOT NULL

(defn- activity-input->link-input
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

;; Statement-to-Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - AttachmentKey: UUID NOT NULL
;; - AttachemntSHA2: STRING NOT NULL

(defn- attachment-input->link-input
  [statement-id {attachment-id :sha2}]
  {:table           :statement-to-attachment
   :primary-key     (generate-uuid)
   :statement-id    statement-id
   :attachment-sha2 attachment-id})

;; Statement
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID UNIQUE KEY NOT NULL
;; - SubStatementID: UUID
;; - StatementRefID: UUID
;; - Timestamp: TIMESTAMP NOT NULL
;; - Stored: TIMESTAMP NOT NULL
;; - Registration: UUID
;; - VerbID: STRING NOT NULL
;; - IsVoided: BOOLEAN NOT NULL DEFAULT FALSE
;; - Data: JSON NOT NULL

(defn statement->hugsql-input
  [statement ?attachments]
  (let [stmt-pk      (generate-uuid)
        stmt-id      (get statement "id")
        stmt-obj     (get statement "object")
        stmt-ctx     (get statement "context")
        stmt-time    (if-let [ts (get statement "timestamp")] ts (current-time))
        stmt-stored  (current-time)
        stmt-reg     (get-in statement ["context" "registration"])
        sub-stmt-id  (when (= "SubStatement" (get stmt-obj "objectType"))
                       (generate-uuid))
        stmt-ref-id  (when (= "StatementRef" (get stmt-obj "objectType"))
                       (get stmt-obj "id"))
        stmt-vrb-id  (get-in statement ["verb" "id"])
        voided?      (= "http://adlnet.gov/expapi/verbs/voided" stmt-vrb-id)
        ;; Statement Agents
        stmt-actr    (get statement "actor")
        stmt-auth    (get statement "authority")
        stmt-inst    (get stmt-ctx "instructor")
        stmt-team    (get stmt-ctx "team")
        obj-agnt-in  (agent->input stmt-obj)
        actr-agnt-in (agent->input stmt-actr)
        auth-agnt-in (agent->input stmt-auth)
        inst-agnt-in (agent->input stmt-inst)
        team-agnt-in (agent->input stmt-team)
        ;; Statement Activities
        cat-acts     (get-in stmt-ctx ["contextActivities" "category"])
        grp-acts     (get-in stmt-ctx ["contextActivities" "grouping"])
        prt-acts     (get-in stmt-ctx ["contextActivities" "parent"])
        oth-acts     (get-in stmt-ctx ["contextActivities" "other"])
        obj-act-in   (when (= "Activity" (get stmt-obj "objectType"))
                       (activity->input stmt-obj))
        cat-acts-in  (when cat-acts (map activity->input cat-acts))
        grp-acts-in  (when grp-acts (map activity->input grp-acts))
        prt-acts-in  (when prt-acts (map activity->input prt-acts))
        oth-acts-in  (when oth-acts (map activity->input oth-acts))
        ;; Statement HugSql input
        stmt-input   {:table             :statement
                      :primary-key       stmt-pk
                      :statement-id      stmt-id
                      :?sub-statement-id sub-stmt-id
                      :?statement-ref-id stmt-ref-id
                      :timestamp         stmt-time
                      :stored            stmt-stored
                      :?registration     stmt-reg
                      :verb-iri          stmt-vrb-id
                      :voided?           voided?
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
        ;; Attachment HugSql input
        att-inputs   (when ?attachments (map attachment->input ?attachments))
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
                       (concat (map (partial act->link "Other") oth-acts-in)))
        ;; Statement-to-Attachment HugSql input
        stmt-atts    (when att-inputs
                       (map (partial attachment-input->link-input stmt-id)
                            att-inputs))]
    (concat [stmt-input]
            agnt-inputs
            act-inputs
            att-inputs
            stmt-agnts
            stmt-acts
            stmt-atts)))

;; State-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StateID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - Registration: UUID
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL
;;
;; Agent-Profile-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL
;;
;; Activity-Profile-Resource
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL

(defn document->hugsql-input
  [id-params ^bytes document]
  (cond
    ;; State Document
    (contains? id-params "stateID")
    {:table         :state-document
     :primary-key   (generate-uuid)
     :state-id      (get id-params "stateID")
     :activity-id   (get id-params "activityID")
     :agent-id      (get id-params "agent")
     :?registration (get id-params "registration")
     :last-modified (current-time)
     :document      document}

    ;; Agent Profile Document
    (contains? id-params "agent")
    {:table         :agent-profile-document
     :primary-key   (generate-uuid)
     :profile-id    (get id-params "profileID")
     :agent-id      (get id-params "agent")
     :last-modified (current-time)
     :document      document}

    ;; Activity Profile Document
    (contains? id-params "activityID")
    {:table         :activity-profile-document
     :primary-key   (generate-uuid)
     :profile-id    (get id-params "profileID")
     :activity-id   (get id-params "activityID")
     :last-modified (current-time)
     :document      document}))

;; TODO
;; Canonical-Language-Maps
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - IRI: STRING UNIQUE KEY NOT NULL
;; - LangTag: STRING NOT NULL
;; - Value: STRING NOT NULL

;; #_{:clj-kondo/ignore [:unresolved-symbol]}
;; (defn insert-hugsql-inputs!
;;   [inputs db]
;;   (doseq [{:keys [table] :as input} inputs]
;;     (case table
;;       :statement
;;       (insert-statement db input)
;;       :agent
;;       (insert-agent db input)
;;       :activity
;;       (insert-activity db input)
;;       :attachment
;;       (insert-activity db input)
;;       :statement-to-agent
;;       (insert-statement-to-agent db input)
;;       :statement-to-activity
;;       (insert-statement-to-activity db input)
;;       :statement-to-attachment
;;       (insert-statement-to-attachment db input))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: format
;; TODO: attachments

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn query-parmas->hugsql-input
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
    attachments?        :attachments
    ascending?          :ascending
    page                :page
    from                :from}]
  (cond-> {}
    statement-id
    (merge {:statement-id-snip
            (statement-id-snip {:statement-id statement-id})
            :is-voided-snip
            (is-voided-snip {:voided? false})})
    voided-statement-id
    (merge {:statement-id-snip
            (statement-id-snip {:statement-id voided-statement-id})
            :is-voided-snip
            (is-voided-snip {:voided? true})})
    verb
    (assoc :verb-iri-snip
           (verb-iri-snip {:verb-iri verb}))
    registration
    (assoc :registration-snip
           (registration-snip {:registration registration}))
    since
    (assoc :timestamp-since-snip
           (timestamp-since-snip {:since since}))
    until
    (assoc :timestamp-until-snip
           (timestamp-until-snip {:until until}))
    agent
    (assoc :statement-to-agent-join-snip
           (statement-to-agent-join-snip
            (cond-> {:agent-ifi (get-ifi agent)}
              (not related-agents?)
              (assoc :actor-agent-usage-snip
                     (actor-agent-usage-snip)))))
    activity
    (assoc :statement-to-activity-join-snip
           (statement-to-activity-join-snip
            (cond-> {:activity-iri activity}
              (not related-activities?)
              (assoc :object-activity-usage-snip
                     (object-activity-usage-snip)))))
    limit
    (assoc :limit-snip
           (limit-snip {:limit limit}))))
