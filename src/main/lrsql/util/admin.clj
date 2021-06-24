(ns lrsql.util.admin
  (:require [buddy.core.bytes :as bb]
            [buddy.core.codecs :as bc]
            [buddy.core.hash :as bh]
            [buddy.core.nonce :as bn]))

(defn hash-password
  [password]
  (let [pass-salt (bn/random-bytes 32)
        pass-hash (bh/blake2b password 32)]
    {:password-hash (bc/bytes->hex (bb/concat pass-hash pass-salt))
     :password-salt (bc/bytes->hex pass-salt)}))

(defn valid-password?
  [password pass-hash pass-salt]
  (let [pass-bytes (bh/blake2b password 32)
        hash-bytes (bc/hex->bytes pass-hash)
        salt-bytes (bc/hex->bytes pass-salt)]
    (= hash-bytes
       (bb/concat pass-bytes salt-bytes))))
