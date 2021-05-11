(ns lrsql.impl
  (:require [com.yetanalytics.lrs.protocol :as p]
            [lrsql.hugsql.input :as input]))

(defrecord LearningRecordStore []
  p/AboutResource
  (-get-about
   [lrs auth-identity]
   ;; TODO: Add 2.X.X versions
   {:body {:version ["1.0.0" "1.0.1" "1.0.2" "1.0.3"]}})

  p/StatementsResource
  (-store-statements
   [lrs auth-identity statements attachments]
   {:statement-ids []})
  (-get-statements
   [this auth-identity params ltags]
   {:statement {:id "my-statement-id"}}) ; TODO: return needs to be a statement
  (-consistent-through
   [lrs ctx auth-identity]
   "timestamp-here") ; TODO: return needs to be a timestamp

  p/DocumentResource
  (-set-document
   [lrs auth-identity params document merge?]
   {})
  (-get-document
   [this auth-identity params]
   {:document nil})
  (-get-document-ids
   [this auth-identity params]
   {:document-ids []})
  (-delete-document
   [this auth-identity params]
   {})
  (-delete-documents
   [this auth-identity params]
   {})

  p/AgentInfoResource
  (-get-person
   [this auth-identity params]
   {:person {:objectType "Person"}})

  p/ActivityInfoResource
  (-get-activity
   [this auth-identity params]
   {:activity {:objectType "Activity"}})

  p/LRSAuth
  (-authenticate
   [this ctx]
   {:result ::forbidden})
  (-authorize
   [this ctx auth-identity]
   {:result false}))
