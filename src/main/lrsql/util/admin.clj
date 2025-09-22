(ns lrsql.util.admin
  (:require [buddy.hashers  :as bh]
            [buddy.sign.jwt :as bj]
            [lrsql.util :as u]
            [clojure.string :refer [split]]))

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

(defn- jwt-claim
  "Create the JWT claim, i.e. payload, containing the account ID `:acc`, the
   issued-at time `:iat`, the expiration time `:exp`, and the refresh
   expiration time `:ref`.

   Time values MUST be a number containing a NumericDate value ie. a JSON
   numeric value representing the number of seconds (not milliseconds!) from
   the 1970 UTC start."
  [account-id ctime etime rtime]
  {:acc account-id
   :iat (quot (u/time->millis ctime) 1000)
   :exp (quot (u/time->millis etime) 1000)
   :ref (quot (u/time->millis rtime) 1000)})

(defn one-time-jwt
  "Create a one-time JWT, with only `:iat`, `:exp` and a unique `:oti` property
   (short for \"one time ID\") in the claim body. If `account-id` and
   `expiration` does not exist (e.g. in a proxy JWT or OIDC login scenario),
   we fall back to `exp` for the expiration. Returns a map containing
   `:jwt` alongside `:exp` and `:oti`."
  [{:keys [account-id expiration]} secret exp]
  (when (or (nil? secret)
            (nil? exp))
    (throw (ex-info "Secret and expiration or JWTs required!"
                    {:error ::cannot-make-jwts})))
  (let [ctime (u/current-time)
        etime (if (and (some? account-id)
                       (some? expiration))
                ;; Regular JWT
                expiration
                ;; Proxy JWT or OIDC
                (u/offset-time ctime exp :seconds))
        otid  (random-uuid)
        claim {:iat (quot (u/time->millis ctime) 1000)
               :exp (quot (u/time->millis etime) 1000) ;seconds from epoch
               :oti otid}]
    {:jwt (bj/sign claim secret)
     :exp (- (:exp claim)  ;seconds from now
             (:iat claim))
     :oti otid}))

(defn account-id->jwt*
  "Same as `account-id->jwt`, but uses a pre-existing `rtime` timestamp instead
   of an `ref` offset."
  [account-id secret exp rtime]
  (let [ctime (u/current-time)
        etime (u/offset-time ctime exp :seconds)
        claim (jwt-claim account-id ctime etime rtime)]
    (bj/sign claim secret)))

(defn account-id->jwt
  "Generate a new signed JSON Web Token with `account-id` in the claim
   as a custom `:acc` field. The issued-at, expiration, and refresh expiration
   times are given as `:iat`, `:exp`, and `:ref`, respectively. The token and
   refresh expiration time offsets are given by `exp` and `ref`, respectively,
   in seconds."
  [account-id secret exp ref]
  (let [ctime (u/current-time)
        etime (u/offset-time ctime exp :seconds)
        rtime (u/offset-time ctime ref :seconds)
        claim (jwt-claim account-id ctime etime rtime)]
    (bj/sign claim secret)))

(defn header->jwt
  "Given an header of the form `Bearer [encoded JWT]`, return the JWT.
   Returns `nil` if the header itself is `nil` or cannot be parsed."
  [auth-header]
  (when auth-header
    (second (re-matches #"Bearer\s+(.*)" auth-header))))

(defn jwt->payload
  "Given the JSON Web Token `tok`, unsign and verify the token using `secret`.
   Return a map of `:account-id`, `:expiration`, `:refresh-exp` and/or
   `:one-time-id`, depending on the contents of `tok` (i.e. `:account-id`
   and `:refresh-exp` are only present in regular account JWTs, while
   `:one-time-id` is unique to one-time JWTs). Otherwise return
   `:lrsql.admin/unauthorized-token-error`.
   `leeway` is a time amount (in seconds) provided to compensate for
   clock drift."
  [tok secret leeway]
  (if tok ; Avoid encountering a null pointer exception
    (try
      (let [{:keys [acc exp ref oti]} (bj/unsign tok secret {:leeway leeway})]
        (cond-> {}
          acc (assoc :account-id (u/str->uuid acc))
          exp (assoc :expiration (u/millis->time (* 1000 exp)))
          ref (assoc :refresh-exp (u/millis->time (* 1000 ref)))
          oti (assoc :one-time-id (u/str->uuid oti))))
      (catch clojure.lang.ExceptionInfo _
        :lrsql.admin/unauthorized-token-error))
    :lrsql.admin/unauthorized-token-error))

(defn proxy-jwt->payload
  "Decode (without validating!) a JWT claim and verify that the role-key on
   the claim contains the expected role. Return a map containing `:username`
   and `:issuer` if valid, otherwise return
   `:lrsql.admin/unauthorized-token-error`."
  [tok uname-key issuer-key role-key role]
  (if tok
    (try
      (let [payload   (-> tok
                          (split #"\.")
                          second
                          u/base64encoded-str->str
                          u/parse-json)
            roles     (get payload role-key)
            has-role? (if (coll? roles)
                        (some? ((set roles) role))
                        (= roles role))]
        (if has-role?
          {:username (get payload uname-key)
           :issuer   (get payload issuer-key)}
          :lrsql.admin/unauthorized-token-error))
      (catch clojure.lang.ExceptionInfo _
        :lrsql.admin/unauthorized-token-error))
    :lrsql.admin/unauthorized-token-error))
