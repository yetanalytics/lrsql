(ns lrsql.input.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.set :as cset]
            ;; Specs
            [lrsql.spec.authority :as as]
            [lrsql.spec.statement :as ss]
            ;; Inputs
            [lrsql.input.actor      :as i-ac]
            [lrsql.input.activity   :as i-av]
            [lrsql.input.attachment :as i-at]
            ;; Utils
            [lrsql.util :as u]
            [lrsql.util.actor :as au]))

(def voiding-verb "http://adlnet.gov/expapi/verbs/voided")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Deal with contextAgents, contextGroups, and any other properties
;; in version 2.0

(defn- insert-stmt-actor-inputs
  "Helper to construct the value for `:actor-inputs` in the statement input
   param map."
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
        ?act-input  (i-ac/insert-actor-input stmt-act)
        ?obj-input  (when actor-obj? (i-ac/insert-actor-input stmt-obj))
        ?auth-input (when ?stmt-auth (i-ac/insert-actor-input ?stmt-auth))
        ?inst-input (when ?stmt-inst (i-ac/insert-actor-input ?stmt-inst))
        ?team-input (when ?stmt-team (i-ac/insert-actor-input ?stmt-team))
        ;; Member Actors
        ?act-mem-inputs  (i-ac/insert-group-input stmt-act)
        ?obj-mem-inputs  (when actor-obj? (i-ac/insert-group-input stmt-obj))
        ?auth-mem-inputs (when ?stmt-auth (i-ac/insert-group-input ?stmt-auth))
        ?inst-mem-inputs (when ?stmt-inst (i-ac/insert-group-input ?stmt-inst))
        ?team-mem-inputs (when ?stmt-team (i-ac/insert-group-input ?stmt-team))
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
        actor->link (partial i-ac/insert-statement-to-actor-input stmt-id)
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

(defn- insert-stmt-activity-inputs
  "Helper to construct the value for `:activity-inputs` in the statement input
   param map."
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
        ?obj-act-in  (when activity-obj? (i-av/insert-activity-input stmt-obj))
        ?cat-acts-in (when ?cat-acts (map i-av/insert-activity-input ?cat-acts))
        ?grp-acts-in (when ?grp-acts (map i-av/insert-activity-input ?grp-acts))
        ?prt-acts-in (when ?prt-acts (map i-av/insert-activity-input ?prt-acts))
        ?oth-acts-in (when ?oth-acts (map i-av/insert-activity-input ?oth-acts))
        ;; Activity Inputs
        act-inputs (cond-> []
                     ?obj-act-in  (conj ?obj-act-in)
                     ?cat-acts-in (concat ?cat-acts-in)
                     ?grp-acts-in (concat ?grp-acts-in)
                     ?prt-acts-in (concat ?prt-acts-in)
                     ?oth-acts-in (concat ?oth-acts-in))
        ;; Statement to Activity Enums
        act->link (partial i-av/insert-statement-to-activity-input stmt-id)
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

(defn- insert-sub-stmt-inputs
  "Helper to create inputs for `sub-statement`, where `stmt-id` is the ID
   of the parent statement."
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
        (insert-stmt-actor-inputs stmt-id
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
        (insert-stmt-activity-inputs stmt-id
                                          sub-stmt-obj
                                          ?sub-stmt-ctx-acts
                                          {:obj-enum "SubObject"
                                           :cat-enum "SubCategory"
                                           :grp-enum "SubGrouping"
                                           :prt-enum "SubParent"
                                           :oth-enum "SubOther"})]
    [actor-inputs activity-inputs stmt-actor-inputs stmt-activity-inputs]))

(s/fdef insert-statement-input
  :args (s/cat :statement ss/prepared-statement-spec)
  :ret ss/insert-statement-input-spec)

(defn insert-statement-input
  "Given `statement`, return the input param map for `insert-statement!` with
   all values except for `:attachment-inputs` and `:stmt-stmt-inputs` set."
  [statement]
  (let [;; Destructuring
        ;; `id` and `authority` should have already been set by
        ;; `prepare-statement`.
        {stmt-id    "id"
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
                    :registration      ?stmt-reg
                    :attachment-shas   att-shas
                    :verb-iri          stmt-vrb-id
                    :voided?           false
                    :voiding?          voiding?
                    :payload           statement}
        ;; Actor HugSql Inputs
        [actor-inputs stmt-actor-inputs]
        (insert-stmt-actor-inputs stmt-id
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
        (insert-stmt-activity-inputs stmt-id
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
          (insert-sub-stmt-inputs stmt-id stmt-obj))]
    {:statement-input      stmt-input
     :actor-inputs         (concat actor-inputs sactor-inputs)
     :activity-inputs      (concat activ-inputs sactiv-inputs)
     :stmt-actor-inputs    (concat stmt-actor-inputs sstmt-actor-inputs)
     :stmt-activity-inputs (concat stmt-activ-inputs sstmt-activ-inputs)
     :stmt-stmt-inputs     []
     :attachment-inputs    []}))

(s/fdef insert-statement-batch-input
  :args (s/cat :statements (s/coll-of ss/prepared-statement-spec
                                      :min-count 1
                                      :gen-max 5))
  :ret (s/coll-of ss/insert-statement-input-spec :min-count 1))

(defn insert-statement-batch-input
  "Given the coll `statements`, return a seq of input param maps that will
   each be passed to `insert-statement!`"
  [statements]
  (map insert-statement-input statements))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion w/ Descendants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-descendant-input
  :args (s/cat :statement-id ::ss/statement-id
               :descendant-id ::ss/statement-id)
  :ret ::ss/stmt-stmt-input)

(defn insert-descendant-input
  "Given `statement-id` and `attachment`, construct an entry in the
   `:stmt-stmt-inputs` vec in the `insert-statement!` input param map."
  [statement-id descendant-id]
  {:table         :statement-to-statement
   :primary-key   (u/generate-squuid)
   :descendant-id descendant-id
   :ancestor-id   statement-id})

(s/fdef add-insert-descendant-inputs
  :args (s/cat :input-map ss/insert-statement-input-spec
               :desc-ids  (s/coll-of uuid?))
  :ret  ss/insert-statement-input-spec)

(defn add-insert-descendant-inputs
  "Given `input-map` and `descendant-ids`, add any descendant IDs to the input
   map in order to be passed to `functions/insert-statement-to-statement!`"
  [input-map descendant-ids]
  (let [stmt-id (-> input-map :statement-input :statement-id)]
    (reduce (fn [input-map' desc-id]
              (update input-map'
                      :stmt-stmt-inputs
                      conj
                      (insert-descendant-input stmt-id desc-id)))
            input-map
            descendant-ids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion w/ Attachments
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Assume that the LRS has already validated that every statement
;; attachment object has a fileUrl or valid SHA2 value.

(s/fdef add-insert-attachment-inputs
  :args ss/stmt-input-attachments-spec
  :ret (s/coll-of ss/insert-statement-input-spec))

(def ^:private duplicate-sha-emsg
  "Some Attachments provided have duplicate SHA2 hashes. Some Attachments may not be stored in the DB successfully.")

(def ^:private bad-attachment-emsg
  "Some Attachments provided are not associated with the Statements in the request.")

(def ^:private attachment-mismatch-type
  :com.yetanalytics.lrs.pedestal.interceptor.xapi.statements.attachment/statement-attachment-mismatch)

;; We may have duplicate SHA2 hashes because:
;; 1. Duplicate attachments are technically allowed.
;; 2. SHA2 collisions are relatively common compared to better algorithns
;; (though still rare in absolute terms).
;; For now, emit an warning message if duplicate hashes occur.

(defn- warn-on-dupe-shas
  [attachments shas]
  (when (not= (count attachments) (count shas))
    (log/warn duplicate-sha-emsg)
    (log/debug "Attachments: %s"
               (mapv #(dissoc % :content) attachments))))

(defn add-insert-attachment-inputs
  "Given `input-maps` and `attachments`, conj each attachment to the
   `:attachment-inputs` vec in the appropriate input param map, i.e. the one
   whose statement contains the attachment's SHA2 hash."
  [stmt-input attachments]
  (if (empty? attachments)
    ;; No attachments - don't bother
    stmt-input
    ;; Attachments present - add to inputs
    (let [sha-att-m
          (into {} (map (fn [{sha :sha2 :as att}] [sha att]) attachments))
          shas
          (set (keys sha-att-m))
          _
          (warn-on-dupe-shas attachments shas)
          add-att-to-stmt-in
          (fn [stmt-in sha]
            (let [att     (sha-att-m sha)
                  stmt-id (-> stmt-in :statement-input :statement-id)
                  att-in  (i-at/insert-attachment-input
                           stmt-id
                           att)]
              (update stmt-in
                      :attachment-inputs
                      conj
                      att-in)))
          result
          (for [stmt-in stmt-input
                :let [?att-shas    (-> stmt-in
                                       :statement-input
                                       :attachment-shas)
                      ?stmt-shas   (and ?att-shas
                                        (cset/intersection shas ?att-shas))
                      new-stmt-ins (reduce
                                    add-att-to-stmt-in
                                    stmt-in
                                    ?stmt-shas)]]
            [new-stmt-ins ?stmt-shas])
          added-shas
          (->> result (map second) (filter some?) (apply cset/union))]
      (if-some [diff-sha (not-empty (cset/difference shas
                                                     added-shas))]
        ;; Some attachments weren't included - throw an error
        (throw (ex-info bad-attachment-emsg
                        {:type         attachment-mismatch-type
                         :attachments  attachments
                         :invalid-shas diff-sha
                         :stmt-inputs  stmt-input}))
        ;; All attachments were included - return new stmt inputs
        (map first result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Invalid query params, e.g. those with both a statementId and a
;; voidedStatementId property, or singleton queries with extra params,
;; would have been filtered out earlier by interceptors.

(s/fdef query-statement-input
  :args (s/cat :params ::ss/query-params
               :authority (s/nilable as/query-authority-spec))
  :ret ss/statement-query-spec)

(defn query-statement-input
  "Given `params`, construct the input for `query-statement!`. The input can be
   for a single-statement query (if it has a statement ID), or a multi-statement
   query (if not). A nilable `?authority` arg can be passed to add additional
   query params based on the authority agents."
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
    ?asc?        :ascending
    limit        :limit ; Ensured by `ensure-default-max-limit`
    ?atts?       :attachments
    ?format      :format
    ?from        :from ; Not a stmt res param; added by lrsql for pagination
    ?url-prefix  :more-url-prefix ; Added by `add-more-url-prefix`
    :as          params}
   ?authority]
  (let [?auth-ifis  (when ?authority (au/actor->ifi-coll ?authority))
        ?stmt-id    (when ?stmt-id (u/str->uuid ?stmt-id))
        ?vstmt-id   (when ?vstmt-id (u/str->uuid ?vstmt-id))
        ?actor-ifi  (when ?actor (au/actor->ifi ?actor))
        ?reg        (when ?reg (u/str->uuid ?reg))
        ?from       (when ?from (u/str->uuid ?from))
        ?since      (when ?since (u/time->uuid (u/str->time ?since)))
        ?until      (when ?until (u/time->uuid (u/str->time ?until)))
        rel-actors? (boolean ?rel-actors?) 
        rel-activs? (boolean ?rel-activs?)
        asc?        (boolean ?asc?)
        format      (if ?format (keyword ?format) :exact)
        atts?       (boolean ?atts?)
        url-prefix  (if ?url-prefix ?url-prefix "")
        comm-params (cond-> {:format       format
                             :attachments? atts?}
                      ?auth-ifis
                      (merge {:authority-ifis      ?auth-ifis
                              :authority-ifi-count (count ?auth-ifis)}))]
    (if-some [stmt-id (or ?stmt-id ?vstmt-id)]
      ;; Single statement query
      (merge comm-params
             {:statement-id stmt-id
              :voided?      (boolean ?vstmt-id)})
      ;; Multiple statement query
      (cond-> comm-params
        true       (assoc :ascending?      asc?
                          :limit           limit
                          :query-params    params
                          :more-url-prefix url-prefix)
        ?actor-ifi (assoc :actor-ifi       ?actor-ifi
                          :related-actors? rel-actors?)
        ?activ-iri (assoc :activity-iri        ?activ-iri
                          :related-activities? rel-activs?)
        ?verb-iri  (assoc :verb-iri ?verb-iri)
        ?reg       (assoc :registration ?reg)
        ?since     (assoc :since ?since)
        ?until     (assoc :until ?until)
        ?from      (assoc :from ?from)))))
