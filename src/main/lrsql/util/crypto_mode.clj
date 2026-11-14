(ns lrsql.util.crypto-mode
  "Describe the JVM's cryptography setup in one startup log line, so the logs
   show whether SQL LRS runs with a FIPS provider and which JCA providers it
   gets for HMAC-SHA256 and secure random numbers."
  (:require [clojure.string :as cstr]
            [clojure.tools.logging :as log])
  (:import [java.security Provider SecureRandom Security]
           [javax.crypto Mac]))

(def crypto-logger-name
  "The logger that reports the crypto mode at startup."
  "lrsql.crypto")

(defmacro ^:private or-unavailable
  [& body]
  `(try ~@body (catch Exception _# "unavailable")))

(defn crypto-mode
  "Return a map describing the JVM's cryptography setup:
   - `:first-provider`: the highest-priority security provider;
   - `:fips?`: true when that provider's name contains \"FIPS\";
   - `:com.redhat.fips`: the value of that system property, or \"unset\";
   - `:kernel-fips`: `/proc/sys/crypto/fips_enabled`, or \"unavailable\";
   - `:hmac-sha256` and `:secure-random`: the provider the JCA picks for
     each by default."
  []
  (let [first-p (or-unavailable
                 (let [ps (Security/getProviders)]
                   (if (pos? (alength ps))
                     (.getName ^Provider (aget ps 0))
                     "none")))]
    {:first-provider  first-p
     :fips?           (cstr/includes? first-p "FIPS")
     :com.redhat.fips (or (System/getProperty "com.redhat.fips") "unset")
     :kernel-fips     (or-unavailable
                       (cstr/trim (slurp "/proc/sys/crypto/fips_enabled")))
     :hmac-sha256     (or-unavailable
                       (.getName (.getProvider (Mac/getInstance "HmacSHA256"))))
     :secure-random   (or-unavailable
                       (let [r (SecureRandom.)]
                         (str (.getAlgorithm r) "/"
                              (.getName (.getProvider r)))))}))

(defn crypto-mode-line
  "Format `mode`, a map from `crypto-mode`, as one log line."
  [{:keys [fips? first-provider kernel-fips hmac-sha256 secure-random]
    redhat-fips :com.redhat.fips}]
  (format (str "Crypto mode: fips=%s first-provider=%s com.redhat.fips=%s"
               " kernel-fips_enabled=%s Mac.HmacSHA256=%s SecureRandom=%s")
          fips? first-provider redhat-fips kernel-fips hmac-sha256
          secure-random))

(defn log-crypto-mode!
  "Log the crypto mode at INFO on `crypto-logger-name`. Never throws."
  []
  (try
    (log/log crypto-logger-name :info nil (crypto-mode-line (crypto-mode)))
    (catch Exception ex
      (log/log crypto-logger-name :warn nil
               (str "Crypto mode unavailable: " (.getName (class ex)))))))
