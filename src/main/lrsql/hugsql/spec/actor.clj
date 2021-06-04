(ns lrsql.hugsql.spec.actor
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.hugsql.spec.util :refer [make-str-spec]]))

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

(s/def ::agent-ifi ::actor-ifi)

(s/def ::actor-type
  #{"Agent" "Group"})

(s/def ::usage
  #{"Actor" "Object" "Authority" "Instructor" "Team"
    "SubActor" "SubObject" "SubAuthority" "SubInstructor" "SubTeam"})

;; JSON string version: :xapi.statements.GET.request.params/agent
(s/def ::payload ::xs/actor)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Agent params spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-agent-params
  ::lrsp/get-person-params)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Agent query spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def agent-query-spec
  (s/keys :req-un [::agent-ifi]))
