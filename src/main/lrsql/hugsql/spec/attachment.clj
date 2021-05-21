(ns lrsql.hugsql.spec.attachment
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec]))

(s/def :lrsql.hugsql.spec.attachment/attachment-sha :attachment/sha2)
(s/def :lrsql.hugsql.spec.attachment/content-type string?)
(s/def :lrsql.hugsql.spec.attachment/content-length int?)
(s/def :lrsql.hugsql.spec.attachment/content bytes?)
