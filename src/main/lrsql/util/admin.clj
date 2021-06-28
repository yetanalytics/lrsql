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
  "Generate a new signed JSON Web Token with `account-id` in the claim
   as a custom `:acc` field. The issued-at and expiration time are given as
   `:iat` and `:exp`, respectively."
  [account-id]
  (let [ctime (u/current-time)
        etime (u/offset-time ctime 1 :hours) ; TODO: offset amount and units in config
        claim {:acc account-id
               ;; Time values MUST be a number containing a NumericDate value
               ;; ie. a JSON numeric value representing the number of seconds
               ;; (not milliseconds!) from the 1970 UTC start.
               :iat (quot (u/time->millis ctime) 1000)
               :exp (quot (u/time->millis etime) 1000)}]
    (bj/sign claim secret))) ; TODO: algorithm

(defn header->jwt
  "Given an authentication header whose value is a JSON Web Token, return
   the encoded JWT."
  [auth-header]
  (second (re-matches #"Bearer\s+(.*)" auth-header)))

(defn jwt->account-id
  "Given the JSON Web Token `tok`, return the account ID if valid.
   Otherwise return one of the following errors:
     `:expired-token-error` - if the token was expired.
     `:invalid-token-error` - every other error."
  [tok]
  (try
    ;; TODO: algorithm
    ;; TODO: leeway in config vars
    (let [ctime (-> (u/current-time) u/time->millis (quot 1000))]
      (-> tok (bj/unsign secret {:now ctime}) :acc u/str->uuid))
    (catch Exception e ; Calls non-ExceptionInfo error on nil token
      (if-some [ed (ex-data e)]
        (if (and (#{:validation} (:type ed))
                 (#{:exp}) (:cause ed))
          :lrsql.admin/expired-token-error
          :lrsql.admin/invalid-token-error)
        :lrsql.admin/invalid-token-error))))
