(ns lrsql.ops.command.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.auth :as as]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Dupe checking

(s/fdef insert-credential!
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :input as/insert-cred-input-spec)
  :ret nil?)

(defn insert-credential!
  "Insert the credential keys in `input` into the DB. Returns `nil`."
  [bk tx input]
  (bp/-insert-credential! bk tx input)
  nil)

(s/fdef insert-credential-scopes!
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :inputs as/insert-cred-scopes-input-spec)
  :ret nil?)

(defn insert-credential-scopes!
  "Insert `input`, a seq of maps where each API key pair is associated
   with a different scope."
  [bk tx input]
  (dorun (map (partial bp/-insert-credential-scope! bk tx) input)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Existence checking

(s/fdef delete-credential-scopes!
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :inputs as/delete-cred-scopes-input-spec)
  :ret nil?)

(defn delete-credential-scopes!
  "Delete the scopes associated with the credential in the `input` seq
   Returns `nil`."
  [bk tx input]
  (dorun (map (partial bp/-delete-credential-scope! bk tx) input)))

(s/fdef delete-credential!
  :args (s/cat :bk as/credential-backend?
               :tx transaction?
               :input as/delete-cred-input-spec)
  :ret nil?)

(defn delete-credential!
  "Delete the credential and all of its scopes associated with the key pair
   in `input`. Returns `nil`."
  [bk tx input]
  (bp/-delete-credential! bk tx input)
  nil)
