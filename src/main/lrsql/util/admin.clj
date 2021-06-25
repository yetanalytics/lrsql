(ns lrsql.util.admin
  (:require [buddy.core.bytes  :as bb]
            [buddy.core.codecs :as bc]
            [buddy.core.hash   :as bh]
            [buddy.core.nonce  :as bn]
            [buddy.sign.jwt    :as bj]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Passwords
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hash-password
  [password]
  (let [salt-bytes (bn/random-bytes 32)
        pass-bytes (bh/blake2b password 32)
        hash-bytes (bb/concat pass-bytes salt-bytes)]
    {:password-hash (bc/bytes->hex hash-bytes)
     :password-salt (bc/bytes->hex salt-bytes)}))

(defn valid-password?
  [password {:keys [password-hash password-salt]}]
  (let [salt-bytes (bc/hex->bytes password-salt)
        pass-bytes (bh/blake2b password 32)]
    (= password-hash
       (bc/bytes->hex (bb/concat pass-bytes salt-bytes)))))

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
    (-> tok (bj/unsign secret) :account-id)
    ;; TODO: Keep throwing an exception for certain values of `:cause`?
    (catch clojure.lang.ExceptionInfo _ nil)))
