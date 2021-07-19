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
  "Insert the credential keys in `input` into the DB. Returns `nil`."
  [tx input]
  (f/insert-credential! tx input)
  nil)

(s/fdef insert-credential-scopes!
  :args (s/cat :tx transaction? :inputs as/insert-cred-scopes-input-spec)
  :ret nil?)

(defn insert-credential-scopes!
  "Insert `input`, a seq of maps where each API key pair is associated
   with a different scope."
  [tx input]
  (dorun (map (partial f/insert-credential-scope! tx) input)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Existence checking

(s/fdef delete-credential-scopes!
  :args (s/cat :tx transaction? :inputs as/delete-cred-scopes-input-spec)
  :ret nil?)

(defn delete-credential-scopes!
  "Delete the scopes associated with the credential in the `input` seq
   Returns `nil`."
  [tx input]
  (dorun (map (partial f/delete-credential-scope! tx) input)))

(s/fdef delete-credential!
  :args (s/cat :tx transaction? :input as/delete-cred-input-spec)
  :ret nil?)

(defn delete-credential!
  "Delete the credential and all of its scopes associated with the key pair
   in `input`. Returns `nil`."
  [tx input]
  (f/delete-credential! tx input)
  nil)
