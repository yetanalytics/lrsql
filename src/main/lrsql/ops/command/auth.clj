(ns lrsql.ops.command.auth
  (:require [clojure.spec.alpha :as s]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.auth :as as]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Dupe checking

(s/fdef insert-credential!
  :args (s/cat :interface c/insert-interface?
               :tx c/transaction?
               :input as/insert-cred-input-spec)
  :ret nil?)

(defn insert-credential!
  "Insert the credential keys in `input` into the DB. Returns `nil`."
  [interface tx input]
  (ip/-insert-credential! interface tx input)
  nil)

(s/fdef insert-credential-scopes!
  :args (s/cat :interface c/insert-interface?
               :tx c/transaction?
               :inputs as/insert-cred-scopes-input-spec)
  :ret nil?)

(defn insert-credential-scopes!
  "Insert `input`, a seq of maps where each API key pair is associated
   with a different scope."
  [interface tx input]
  (dorun (map (partial ip/-insert-credential-scope! interface tx) input)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Credential Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Existence checking

(s/fdef delete-credential-scopes!
  :args (s/cat :interface c/delete-interface?
               :tx c/transaction?
               :inputs as/delete-cred-scopes-input-spec)
  :ret nil?)

(defn delete-credential-scopes!
  "Delete the scopes associated with the credential in the `input` seq
   Returns `nil`."
  [interface tx input]
  (dorun (map (partial ip/-delete-credential-scope! interface tx) input)))

(s/fdef delete-credential!
  :args (s/cat :interface c/delete-interface?
               :tx c/transaction?
               :input as/delete-cred-input-spec)
  :ret nil?)

(defn delete-credential!
  "Delete the credential and all of its scopes associated with the key pair
   in `input`. Returns `nil`."
  [interface tx input]
  (ip/-delete-credential! interface tx input)
  nil)
