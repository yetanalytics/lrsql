(ns lrsql.hugsql.spec.attachment
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec]))

(s/def ::attachment-sha :attachment/sha2)
(s/def ::content-type string?)
(s/def ::content-length int?)
(s/def ::content bytes?)
