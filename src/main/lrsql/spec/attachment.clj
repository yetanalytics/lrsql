(ns lrsql.spec.attachment
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn attachment-backend?
  [bk]
  (satisfies? bp/AttachmentBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::attachment-sha :attachment/sha2)
(s/def ::attachment-shas
  (s/coll-of ::attachment-sha :kind set? :gen-max 5))

(s/def ::content-type :attachment/contentType)
(s/def ::content-length :attachment/length)
(s/def ::contents bytes?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insertion spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Attachment
;; - id:             SEQUENTIAL UUID NOT NULL PRIMARY KEY
;; - statement_key:  UUID NOT NULL FOREIGN KEY
;; - attachment_sha: STRING NOT NULL
;; - content_type:   STRING NOT NULL
;; - content_length: INTEGER NOT NULL
;; - contents:       BINARY NOT NULL

(s/def ::attachment-input
  (s/keys :req-un [::c/primary-key
                   ::c/statement-id
                   ::attachment-sha
                   ::content-type
                   ::content-length
                   ::contents]))

(s/def ::attachment-inputs
  (s/coll-of ::attachment-input :gen-max 5))
