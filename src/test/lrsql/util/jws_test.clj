(ns lrsql.util.jws-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as cstr]
            [buddy.sign.jwt :as bj]
            [cheshire.core :as json]
            [lrsql.util.jws :as jws])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.util Base64 Random UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- b64url ^String [^bytes bs]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bs))

(defn- unb64url ^bytes [^String s]
  (.decode (Base64/getUrlDecoder) s))

(defn- utf8 ^bytes [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(defn- now-s [] (quot (System/currentTimeMillis) 1000))

(defn- hmac-b64
  "The HS256 signature of `signing-input` under `secret`, base64url."
  [secret signing-input]
  (b64url (#'jws/signature secret signing-input)))

(defn- token-with
  "A token with an arbitrary header map and claims map, correctly signed with
   HS256 over exactly those bytes."
  [header claims secret]
  (let [input (str (b64url (utf8 (json/generate-string header)))
                   "."
                   (b64url (utf8 (json/generate-string claims))))]
    (str input "." (hmac-b64 secret input))))

(defn- refused?
  "True when `jws/unsign` refuses `token` with a `{:type :validation}`
   ex-info."
  ([token] (refused? token "secret" 1))
  ([token secret leeway]
   (try (jws/unsign token secret leeway)
        false
        (catch clojure.lang.ExceptionInfo e
          (= :validation (:type (ex-data e)))))))

(defn- live-claims []
  (let [now (now-s)]
    {:acc (str (UUID/randomUUID)) :iat now :exp (+ now 600) :ref (+ now 86400)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Equivalence with buddy-sign
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private secret-chars
  ;; ASCII, Latin-1, CJK, and a supplementary-plane character (4 UTF-8 bytes)
  (vec (concat (map str (map char (range 32 127)))
               ["é" "ß" "ü" "日" "本" "語" "😀"])))

(defn- random-secret
  "A random String of 1 to 128 UTF-8 bytes, mixing ASCII and non-ASCII."
  [^Random r]
  (let [target (inc (.nextInt r 128))]
    (loop [s ""]
      (let [c    (nth secret-chars (.nextInt r (count secret-chars)))
            next (str s c)]
        (if (> (alength (utf8 next)) target)
          (if (empty? s) "a" s)
          (recur next))))))

(defn- random-claims
  "Account token claims in the order acc, iat, exp, ref, then optionally sid
   and jti, with values of the kinds the admin tokens use."
  [^Random r]
  (let [iat (- (now-s) (.nextInt r 1000))
        c4  (array-map :acc (UUID. (.nextLong r) (.nextLong r))
                       :iat iat
                       :exp (+ iat 2000 (.nextInt r 86400))
                       :ref (+ iat 86400 (.nextInt r 86400)))
        rid #(b64url (let [b (byte-array 16)] (.nextBytes r b) b))]
    (cond-> c4
      (.nextBoolean r) (assoc :sid (rid))
      (.nextBoolean r) (assoc :jti (rid)))))

(deftest buddy-equivalence-test
  (let [r (Random. 20261114)]
    (testing "1,000 generated cases: identical bytes, and each verifies the other"
      (dotimes [_ 1000]
        (let [claims (random-claims r)
              secret (random-secret r)
              ours   (jws/sign claims secret)
              buddys (bj/sign claims secret)]
          (is (= buddys ours))
          (is (= (bj/unsign buddys secret {:leeway 1})
                 (jws/unsign buddys secret 1)))
          (is (= (jws/unsign ours secret 1)
                 (bj/unsign ours secret {:leeway 1}))))))
    (testing "one-time token claims (iat, exp, oti, jti)"
      (let [now    (now-s)
            claims (array-map :iat now :exp (+ now 3600)
                              :oti (UUID/randomUUID) :jti (jws/random-id))]
        (is (= (bj/sign claims "secret") (jws/sign claims "secret")))))
    (testing "a byte-array key is used as is, as buddy-core uses it"
      (let [k      (byte-array (range 40))
            claims (live-claims)]
        (is (= (bj/sign claims k) (jws/sign claims k)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; RFC 7515 Appendix A.1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private a1-key
  (unb64url "AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow"))

(def ^:private a1-input
  (str "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9"
       ".eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ"))

(def ^:private a1-sig "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

(deftest rfc7515-a1-test
  (testing "the published signature over the published input"
    (is (= a1-sig (hmac-b64 a1-key a1-input))))
  (testing "the published token verifies (its exp, 1300819380, is in the
            past, so the leeway covers the time since)"
    (let [leeway (+ 60 (- (now-s) 1300819380))
          claims (jws/unsign (str a1-input "." a1-sig) a1-key leeway)]
      (is (= "joe" (:iss claims)))
      (is (= 1300819380 (:exp claims)))))
  (testing "without that leeway it is expired"
    (is (refused? (str a1-input "." a1-sig) a1-key 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Refusals
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest refusal-test
  (let [claims  (live-claims)
        good    (jws/sign claims "secret")
        [h p s] (cstr/split good #"\.")]
    (testing "the good token verifies"
      (is (= (:acc claims) (:acc (jws/unsign good "secret" 1)))))
    (testing "alg other than exactly HS256"
      (doseq [alg ["none" "HS512" "RS256" "hs256" "" "HS256 "]]
        (is (refused? (token-with {:alg alg} claims "secret")) alg))
      (is (refused? (token-with {:typ "JWT"} claims "secret")) "missing alg")
      (is (refused? (token-with {:alg nil} claims "secret")) "null alg")
      (is (refused? (token-with {:alg ["HS256"]} claims "secret")) "array alg"))
    (testing "a header that is not a JSON object"
      (doseq [hdr ["[\"HS256\"]" "\"HS256\"" "42" "null" "{"]]
        (let [input (str (b64url (utf8 hdr)) "." p)]
          (is (refused? (str input "." (hmac-b64 "secret" input))) hdr))))
    (testing "alg none, with an empty and with a plausible signature"
      (let [nh (b64url (utf8 "{\"alg\":\"none\"}"))]
        (is (refused? (str nh "." p ".")))
        (is (refused? (str nh "." p "." s)))
        (is (refused? (str nh "." p "." (hmac-b64 "secret" (str nh "." p)))))))
    (testing "two and four parts"
      (is (refused? (str h "." p)))
      (is (refused? (str h "." p "." s "." s)))
      (is (refused? (str good ".")))
      (is (refused? "not-a-jwt")))
    (testing "a character outside base64url"
      (is (refused? (str h "." p "." (subs s 0 10) "+" (subs s 11))))
      (is (refused? (str h "." p "." s "=")))
      (is (refused? (str h "." (subs p 0 5) "/" (subs p 6) "." s))))
    (testing "one bit flipped in the signature"
      (let [sig (unb64url s)]
        (aset-byte sig 7 (unchecked-byte (bit-xor (aget sig 7) 1)))
        (is (refused? (str h "." p "." (b64url sig))))))
    (testing "a truncated and an extended signature"
      (is (refused? (str h "." p "." (subs s 0 (dec (count s))))))
      (is (refused? (str h "." p "." (b64url (byte-array 31)))))
      (is (refused? (str h "." p "." s "AAAA")))
      (is (refused? (str h "." p "." (b64url (byte-array 33))))))
    (testing "a different key"
      (is (refused? good "secret2" 1))
      (is (refused? (jws/sign claims "secret2"))))
    (testing "an exp that is not a number"
      (is (refused? (jws/sign (assoc claims :exp "9999999999") "secret"))))
    (testing "claims that are not a JSON object"
      (let [input (str h "." (b64url (utf8 "[1,2]")))]
        (is (refused? (str input "." (hmac-b64 "secret" input))))))
    (testing "not a string"
      (is (refused? nil))
      (is (refused? 42)))))

(deftest expiry-boundary-test
  (let [now    1800000000
        leeway 1]
    (with-redefs-fn {#'jws/now-seconds (constantly now)}
      (fn []
        (testing "exp = now - leeway is refused, as buddy-sign refuses it"
          (is (refused? (jws/sign {:iat 1 :exp (- now leeway)} "secret")
                        "secret" leeway)))
        (testing "exp = now - leeway + 1 is accepted"
          (is (some? (jws/unsign (jws/sign {:iat 1 :exp (inc (- now leeway))} "secret")
                                 "secret" leeway))))
        (testing "no exp is accepted, as buddy-sign accepts it"
          (is (some? (jws/unsign (jws/sign {:iat 1} "secret") "secret" leeway))))
        (testing "nbf after now + leeway is refused, as buddy-sign refuses it"
          (is (refused? (jws/sign {:exp (+ now 100) :nbf (+ now leeway 1)} "secret")
                        "secret" leeway))
          (is (some? (jws/unsign (jws/sign {:exp (+ now 100) :nbf (+ now leeway)} "secret")
                                 "secret" leeway))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constant-time comparison
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest constant-time-test
  (testing "verification compares the signature through bytes-equal?"
    (let [calls (atom [])
          orig  jws/bytes-equal?
          tok   (jws/sign (live-claims) "secret")]
      (with-redefs [jws/bytes-equal? (fn [a b]
                                       (swap! calls conj [(alength ^bytes a)
                                                          (alength ^bytes b)])
                                       (orig a b))]
        (jws/unsign tok "secret" 1)
        (is (= [[32 32]] @calls)))))
  (testing "a signature of the wrong length never reaches the comparison"
    (let [calls (atom 0)
          [h p] (cstr/split (jws/sign (live-claims) "secret") #"\.")]
      (with-redefs [jws/bytes-equal? (fn [_ _] (swap! calls inc) true)]
        (is (refused? (str h "." p "." (b64url (byte-array 31)))))
        (is (zero? @calls)))))
  (testing "bytes-equal? is MessageDigest/isEqual"
    (let [a (byte-array [1 2 3]) b (byte-array [1 2 3]) c (byte-array [1 2 4])]
      (is (= (MessageDigest/isEqual a b) (jws/bytes-equal? a b) true))
      (is (= (MessageDigest/isEqual a c) (jws/bytes-equal? a c) false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Random IDs and the startup check
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest random-id-test
  (let [ids (repeatedly 1000 jws/random-id)]
    (is (= 1000 (count (set ids))))
    (is (every? #(re-matches #"[A-Za-z0-9_-]{22}" %) ids))
    (is (every? #(= 16 (alength (unb64url %))) ids))))

(deftest check-key-test
  (testing "a usable key passes"
    (is (nil? (jws/check-key! (str (UUID/randomUUID) (UUID/randomUUID))))))
  (testing "an empty key, which the JCA refuses, stops startup"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"LRSQL_JWT_COMMON_SECRET"
                          (jws/check-key! ""))))
  (testing "a key the provider refuses stops startup, naming the setting,
            not the value"
    (let [value (str "marker-" (UUID/randomUUID))]
      (with-redefs-fn {#'jws/mac (fn [_] (throw (java.security.InvalidKeyException.
                                                 "refused")))}
        (fn []
          (let [e (try (jws/check-key! value) nil
                       (catch clojure.lang.ExceptionInfo e e))]
            (is (some? e))
            (is (= ::jws/key-refused (:type (ex-data e))))
            (is (not (cstr/includes? (pr-str (ex-message e) (ex-data e))
                                     value)))))))))
