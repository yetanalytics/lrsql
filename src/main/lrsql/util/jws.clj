(ns lrsql.util.jws
  "HS256 compact JWS signing and verification for admin tokens.

   Tokens are byte for byte what `buddy.sign.jwt/sign` produces for the same
   claims and key: the header is `{\"alg\":\"HS256\"}`, the payload is
   cheshire's JSON of the claims map in its own order, and every part is
   base64url without padding. The difference is where the HMAC is computed:
   here it goes through `javax.crypto.Mac`, so it comes from the JVM's
   configured security providers (for example a FIPS provider in a FIPS-mode
   JVM). buddy-core computes it with BouncyCastle's lightweight `HMac` class,
   outside the provider list.

   Verification keeps buddy-sign's checks (the signature, `exp` and `nbf` with
   the same leeway and inequalities), compares the signature in constant time,
   and also refuses a header whose `alg` is not exactly \"HS256\". Every
   refusal is an `ex-info` with `{:type :validation}`, as buddy-sign's are."
  (:require [cheshire.core :as json]
            [clojure.string :as cstr])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest SecureRandom]
           [java.util Base64]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Encoding and random IDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- b64url-encode
  ^String [^bytes bs]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bs))

(defn- utf8
  ^bytes [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(def ^:private ^SecureRandom rng
  (SecureRandom.))

(defn random-id
  "Return 16 random bytes (128 bits), base64url-encoded without padding
   (22 characters)."
  []
  (let [bs (byte-array 16)]
    (.nextBytes rng bs)
    (b64url-encode bs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HMAC
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- key-bytes
  "The HMAC key bytes, derived as buddy-core derives them: a String's UTF-8
   bytes, or a byte array as is. Anything else is refused."
  ^bytes [secret]
  (cond
    (string? secret) (utf8 secret)
    (bytes? secret)  secret
    :else
    (throw (IllegalArgumentException.
            (str "No HMAC key can be derived from a "
                 (if (nil? secret) "nil value" (.getName (class secret))))))))

(defn- mac
  "Return a new HmacSHA256 `Mac` keyed with `secret`. A new instance is made
   for each call, since `Mac` instances are not thread-safe."
  ^Mac [secret]
  (doto (Mac/getInstance "HmacSHA256")
    (.init (SecretKeySpec. (key-bytes secret) "HmacSHA256"))))

(defn- signature
  ^bytes [secret ^String signing-input]
  (.doFinal (mac secret) (.getBytes signing-input StandardCharsets/US_ASCII)))

(defn bytes-equal?
  "Compare two byte arrays in constant time."
  [^bytes a ^bytes b]
  (MessageDigest/isEqual a b))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sign and verify
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private header-part
  ;; The header as buddy-sign writes it for its default `:hs256`.
  (b64url-encode (utf8 (json/generate-string {:alg "HS256"}))))

(defn sign
  "Sign the `claims` map with HS256 and `secret`, and return the compact JWS.
   The claims are serialized in the map's own order. Claim values should be
   JSON values (numbers, strings, UUIDs); unlike `buddy.sign.jwt/sign`, dates
   are not converted."
  [claims secret]
  (let [payload (b64url-encode (utf8 (json/generate-string claims)))
        input   (str header-part "." payload)]
    (str input "." (b64url-encode (signature secret input)))))

(defn- invalid!
  [cause]
  (throw (ex-info "Message seems corrupt or manipulated."
                  {:type :validation :cause cause})))

(def ^:private b64url-re #"[A-Za-z0-9_-]+")

(defn- decode-part
  ^bytes [^String part cause]
  (when-not (and part (re-matches b64url-re part))
    (invalid! cause))
  (try
    (.decode (Base64/getUrlDecoder) part)
    (catch IllegalArgumentException _
      (invalid! cause))))

(defn- parse-json
  [^bytes bs keywords? cause]
  (try
    (json/parse-string (String. bs StandardCharsets/UTF_8) keywords?)
    (catch Exception _
      (invalid! cause))))

(defn- now-seconds
  []
  (quot (System/currentTimeMillis) 1000))

(defn- validate-times!
  "The time checks `buddy.sign.jwt/unsign` makes, with the same inequalities:
   refused when an `exp` claim is `<= now - leeway`, or when an `nbf` claim is
   `> now + leeway`."
  [{:keys [exp nbf]} leeway]
  (let [now (now-seconds)]
    (when (some? exp)
      (when (or (not (number? exp))
                (<= exp (- now leeway)))
        (invalid! :exp)))
    (when (some? nbf)
      (when (or (not (number? nbf))
                (> nbf (+ now leeway)))
        (invalid! :nbf)))))

(defn unsign
  "Verify the compact JWS `token` with `secret`, and return its claims with
   keyword keys. `leeway` is in seconds."
  [token secret leeway]
  (when-not (string? token)
    (invalid! :token))
  (let [parts   (cstr/split token #"\." -1)
        _       (when-not (= 3 (count parts))
                  (invalid! :parts))
        [h p s] parts
        header  (parse-json (decode-part h :header) false :header)
        _       (when-not (and (map? header)
                               (= "HS256" (get header "alg")))
                  (invalid! :header))
        payload (decode-part p :payload)
        sig     (decode-part s :signature)
        want    (signature secret (str h "." p))]
    (when-not (and (= (alength sig) (alength want))
                   (bytes-equal? sig want))
      (invalid! :signature))
    (let [claims (parse-json payload true :payload)]
      (when-not (map? claims)
        (invalid! :payload))
      (validate-times! claims leeway)
      claims)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Startup check
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private key-refused-msg
  (str "The admin token signing key cannot be used: LRSQL_JWT_COMMON_SECRET is"
       " empty or was refused by the crypto provider (when it is unset, the"
       " keystore's private key is used)."))

(defn check-key!
  "Sign and verify one throwaway token with `secret`, so that a key the crypto
   provider refuses (for example an empty one) stops startup instead of
   failing every sign-in. Throws an `ex-info` that names the setting but never
   the value."
  [secret]
  (try
    (let [now (now-seconds)]
      (unsign (sign {:iat now :exp (+ now 60) :jti (random-id)} secret)
              secret
              0))
    nil
    (catch Exception e
      (throw (ex-info key-refused-msg
                      {:type        ::key-refused
                       :cause-class (.getName (class e))})))))
