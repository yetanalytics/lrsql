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

(defn account-id->jwt
  "Generate a new signed JSON Web Token with `account-id` in the claim
   as a custom `:acc` field. The issued-at and expiration time are given as
   `:iat` and `:exp`, respectively; the expiration time offset is given by
   `exp` in seconds."
  [account-id secret exp]
  (let [ctime (u/current-time)
        etime (u/offset-time ctime exp :seconds)
        claim {:acc account-id
               ;; Time values MUST be a number containing a NumericDate value
               ;; ie. a JSON numeric value representing the number of seconds
               ;; (not milliseconds!) from the 1970 UTC start.
               :iat (quot (u/time->millis ctime) 1000)
               :exp (quot (u/time->millis etime) 1000)}]
    (bj/sign claim secret)))

(defn header->jwt
  "Given an header of the form `Bearer [encoded JWT]`, return the JWT.
   Returns `nil` if the header itself is `nil` or cannot be parsed."
  [auth-header]
  (when auth-header
    (second (re-matches #"Bearer\s+(.*)" auth-header))))

(defn jwt->account-id
  "Given the JSON Web Token `tok`, return the account ID if valid.
   Otherwise return `:lrsql.admin/unauthorized-token-error`.
   `leeway` is a time amount (in seconds) provided to compensate for
   clock drift."
  [tok secret leeway]
  (if tok ; Avoid encountering a null pointer exception
    (try
      (-> tok (bj/unsign secret {:leeway leeway}) :acc u/str->uuid)
      (catch clojure.lang.ExceptionInfo _
        :lrsql.admin/unauthorized-token-error))
    :lrsql.admin/unauthorized-token-error))
