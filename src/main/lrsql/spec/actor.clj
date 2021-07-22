(ns lrsql.spec.actor
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]
            [lrsql.spec.util :refer [make-str-spec]]))

;; In this context, "Actor" is a catch-all term to refer to both Agents and
;; Identified Groups, not the Actor object within Statements.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn actor-backend?
  [bk]
  (satisfies? bp/ActorBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; "mbox::mailto:foo@example.com"
(def ifi-mbox-spec
  (make-str-spec ::xs/mailto-iri
                 (fn [s] (->> s (re-matches #"mbox::(.*)") second))
                 (fn [s] (->> s (str "mbox::")))))

;; "mbox_sha1sum::123456789ABCDEF123456789ABCDEF123456789A" 
(def ifi-mbox-sha1sum-spec ;
  (make-str-spec ::xs/sha1sum
                 (fn [s] (->> s (re-matches #"mbox_sha1sum::(.*)") second))
                 (fn [s] (->> s (str "mbox_sha1sum::")))))

;; "openid::http://example.org/bar"
(def ifi-openid-spec
  (make-str-spec ::xs/openid
                 (fn [s] (->> s (re-matches #"openid::(.*)") second))
                 (fn [s] (->> s (str "openid::")))))

;; "account::alice@http://example.org"
(def ifi-account-spec
  (make-str-spec ::xs/account
                 (fn [s]
                   (let [[_ nm hp] (re-matches #"account::(.*)@(.*)" s)]
                     {:account/name nm :account/homePage hp}))
                 (fn [{nm "name" hp "homePage"}]
                   (str "account::" nm "@" hp))))

(s/def ::actor-ifi
  (s/or :mbox ifi-mbox-spec
        :mbox-sha1sum ifi-mbox-sha1sum-spec
        :openid ifi-openid-spec
        :account ifi-account-spec))

(s/def ::actor-type
  #{"Agent" "Group"})

(s/def ::usage
  #{"Actor" "Object" "Authority" "Instructor" "Team"
    "SubActor" "SubObject" "SubAuthority" "SubInstructor" "SubTeam"})

;; JSON string version: :xapi.statements.GET.request.params/agent
(s/def ::payload ::xs/actor)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Actor
;; - id:          SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - actor_ifi:   STRING NOT NULL UNIQUE KEY
;; - actor_type:  ENUM ('Agent', 'Group') NOT NULL
;; - payload:     JSON NOT NULL

(s/def ::actor-input
  (s/keys :req-un [::c/primary-key
                   ::actor-ifi
                   ::actor-type
                   ::payload]))

(s/def ::actor-inputs
  (s/coll-of ::actor-input :gen-max 5))

;; Statement-to-Actor
;; - id:           SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_id: UUID NOT NULL FOREIGN KEY
;; - usage:        ENUM ('Actor', 'Object', 'Authority', 'Instructor', 'Team',
;;                       'SubActor', 'SubObject', 'SubAuthority', 'SubInstructor', 'SubTeam')
;;                 NOT NULL
;; - actor_ifi:    STRING NOT NULL FOREIGN KEY

(s/def ::stmt-actor-input
  (s/keys :req-un [::c/primary-key
                   ::c/statement-id
                   ::usage
                   ::actor-ifi
                   ::actor-type]))

(s/def ::stmt-actor-inputs
  (s/coll-of ::stmt-actor-input :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-agent-spec
  (s/and (s/keys :req-un [::actor-ifi
                          ::actor-type])
         #(= "Agent" (:actor-type %))))
