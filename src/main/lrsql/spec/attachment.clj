(ns lrsql.spec.attachment
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec]))

(s/def ::attachment-sha :attachment/sha2)
(s/def ::attachment-shas
  (s/coll-of ::attachment-sha :kind set? :gen-max 5))

(s/def ::content-type :attachment/contentType)
(s/def ::content-length :attachment/length)
(s/def ::contents bytes?)
