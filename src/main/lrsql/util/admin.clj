(ns lrsql.util.admin
  (:require [buddy.hashers  :as bh]
            [buddy.sign.jwt :as bj]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Passwords
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hash-password
  "Encrypt and hash `password` and return a passhash string. Uses BCrypt's
   SHA-512 algorithm and a random 16-byte salt under the hood."
  [password]
  (bh/derive password))

(defn valid-password?
  "Verifies that `password` hashes to `passhash` and returns a boolean."
  [password passhash]
  (:valid (bh/verify password passhash))) ; TODO: Deal with :update property

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Web Tokens
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (def private-key "foo" #_(bk/private-key ""))
;; (def public-key "bar" #_(bk/public-key ""))

(def secret "sandwich") ; TODO: Actual public + private keys

(defn account-id->jwt
  "Generate a new signed JSON Web Token with `account-id` in the claim."
  [account-id]
  (bj/sign {:account-id account-id} secret)) ; TODO: algorithm

(defn jwt->account-id
  "Given the JSON Web Token `tok`, return nil if it is is invalid (e.g. if
   the token has expired), otherwise return the account ID."
  [tok]
  (try
    ;; TODO: algorithm
    (-> tok (bj/unsign secret) :account-id u/str->uuid)
    ;; TODO: Keep throwing an exception for certain values of `:cause`?
    (catch clojure.lang.ExceptionInfo _ nil)))
