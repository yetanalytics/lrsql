(ns lrsql.ops.command.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.functions :as f]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.auth :as as]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Dupe checking

(s/fdef insert-credential!
  :args (s/cat :tx transaction? :input as/insert-cred-input-spec)
  :ret nil?)

(defn insert-credential!
  [tx input]
  (f/insert-credential! tx input)
  nil)

(s/fdef insert-credential-scopes!
  :args (s/cat :tx transaction? :inputs as/insert-cred-scopes-input-spec)
  :ret nil?)

(defn insert-credential-scopes!
  "Insert a seq of credential inputs, each which may have a different scope
   (but should have the same API keys)."
  [tx inputs]
  (dorun (map (partial f/insert-credential-scope! tx) inputs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Existence checking

(s/fdef delete-credential-scopes!
  :args (s/cat :tx transaction? :inputs as/delete-cred-scopes-input-spec)
  :ret nil?)

(defn delete-credential-scopes!
  [tx inputs]
  (dorun (map (partial f/delete-credential-scope! tx) inputs)))

(s/fdef delete-credential!
  :args (s/cat :tx transaction? :input as/delete-cred-input-spec)
  :ret nil?)

(defn delete-credential!
  [tx input]
  (f/delete-credential! tx input)
  nil)
