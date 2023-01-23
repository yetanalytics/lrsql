(ns lrsql.spec.authority
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.spec.common :as c]
            [lrsql.spec.actor :as hs-actor]))

(s/def ::authority-url ::xs/irl)
(s/def ::cred-id ::c/primary-key)
(s/def ::account-id ::c/primary-key)

(s/def ::context-map
  (s/keys :req-un [::authority-url
                   ::cred-id
                   ::account-id]))

(s/def ::authority-fn
  (s/fspec
   :args (s/cat :context-map ::context-map)
   :ret ::xs/agent))

(def query-authority-spec
  :statement/authority)

(s/def ::authority-ifis
  (s/coll-of ::hs-actor/actor-ifi :min-count 1))
