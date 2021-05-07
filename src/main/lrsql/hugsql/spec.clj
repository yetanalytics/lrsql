(ns lrsql.hugsql.spec
  (:require [clojure.spec.alpha :as s]
            [xapi-schema :as xs]))

(def statement-input-spec
  (s/keys :req-un [::id
                   ::statement-id
                   ::?sub-statement-ref
                   ::?statement-ref-id
                   ::timestamp
                   ::stored
                   ::registration
                   ::verb-iri
                   ::voided?
                   :lrsql.hugsql.spec.statement/data]))

(def activity-input-spec
  (s/keys :req-un [::id
                   ::activity-iri
                   :lrsql.hugsql.spec.activity/data]))

(def agent-input-spec
  (s/keys :req-un [::id
                   :lrsql.hugsql.spec.agent/name
                   :lrsql.hugsql.spec.agent/mbox
                   :lrsql.hugsql.spec.agent/mbox-sha1sum
                   :lrsql.hugsql.spec.agent/openid
                   :lrsql.hugsql.spec.agent/account-name
                   :lrsql.hugsql.spec.agent/account-homepage
                   :lrsql.hugsql.spec.agent/identified-group?]))
