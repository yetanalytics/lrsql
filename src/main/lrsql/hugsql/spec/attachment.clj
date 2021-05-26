(ns lrsql.hugsql.spec.attachment
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec]))

(s/def ::attachment-sha :attachment/sha2)
(s/def ::content-type :attachment/contentType)
(s/def ::content-length :attachment/length)
(s/def ::contents bytes?)
