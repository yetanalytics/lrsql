(ns lrsql.impl
  (:require [com.yetanalytics.lrs.protocol :as p]))

(defrecord LearningRecordStore []
  p/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  p/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
   nil)
  (-get-statements
   [lrs auth-identity statements attachments]
   nil)
  (-consistent-through
   [lrs ctx auth-identity]
   nil)

  p/DocumentResource
  (-set-document
   [lrs auth-identity params document merge?]
   nil)
  (-get-document
   [this auth-identity params]
   nil)
  (-get-document-ids
   [this auth-identity params]
   nil)
  (-delete-document
   [this auth-identity params]
   nil)
  (-delete-documents
   [this auth-identity params]
   nil)

  p/AgentInfoResource
  (-get-person
   [this auth-identity params]
   nil)

  p/ActivityInfoResource
  (-get-activity
   [this auth-identity params]
   nil)

  p/LRSAuth
  (-authenticate
   [this ctx]
   nil)
  (-authorize
   [this ctx auth-identity]
   nil))
