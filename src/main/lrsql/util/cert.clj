(ns lrsql.util.cert
  (:require
   [clojure.java.io :refer [input-stream]]
   [clojure.string :as cs]
   [clojure.tools.logging :as log]
   [jdk.security.Key :as k]
   [jdk.security.KeyStore :as ks]
   [less.awful.ssl :as las])
  (:import
   [java.security
    KeyStore
    KeyPair
    KeyPairGenerator
    PrivateKey
    PublicKey
    SecureRandom]
   [java.security.cert
    Certificate]
   [sun.security.x509
    X509CertImpl]
   [org.bouncycastle.x509
    X509V3CertificateGenerator]
   [org.bouncycastle.asn1.x509
    X509Name]))

(defn bc-selfie
  [& {:keys [^String dn
             ^Long days
             ^String alg]
      :or   {dn   "CN=com.yetanalytics.lrsql,OU=Dev,O=Yet Analytics,L=Baltimore,ST=Maryland,C=US"
             days 365000 ;; 1000 years of darkness
             alg  "SHA256withRSA"}}]
  (let [^KeyPairGenerator kpg         (KeyPairGenerator/getInstance "RSA")
        ^KeyPair key-pair             (.generateKeyPair kpg)
        ^PrivateKey private-key       (.getPrivate key-pair)
        ^PublicKey public-key       (.getPublic key-pair)
        ^java.util.Date from          (new java.util.Date)
        ^java.lang.Long to-millis     (-> from .getTime (+ (* days 86400000)))
        ^java.util.Date to            (new java.util.Date to-millis)
        ^BigInteger sn                (new BigInteger 64 (new SecureRandom))
        ^X509Name issuer              (new X509Name dn)
        generator
        (doto (new X509V3CertificateGenerator)
          (.setSerialNumber sn)
          (.setSubjectDN issuer)
          (.setIssuerDN issuer)
          (.setNotBefore from)
          (.setNotAfter to)
          (.setSignatureAlgorithm alg)
          (.setPublicKey public-key))]
    {:cert     (.generate generator private-key)
     :key-pair key-pair}))

(defn selfie-keystore
  "Create a key store + private key with a self-signed cert"
  ^KeyStore
  [^String key-alias
   ^String key-password
   & selfie-args]
  (let [{:keys
         [^X509CertImpl cert
          ^KeyPair key-pair]} (apply bc-selfie selfie-args)]
    {:keystore (doto (KeyStore/getInstance
                      (KeyStore/getDefaultType))
                 (.load nil nil)
                 (.setKeyEntry key-alias
                               (.getPrivate key-pair)
                               (char-array key-password)
                               (into-array Certificate [cert])))
     :private-key (slurp
                   (.. key-pair
                       getPrivate
                       getEncoded))}))

(defn load-chain
  "Load up the cert chain"
  [cert-chain]
  (some-> cert-chain
          (cs/split #",")
          not-empty
          (some->>
           (map las/load-certificate)
           (into-array Certificate))))

(defn cert-keystore
  "Create a key store from cert files"
  ^KeyStore
  [^String key-alias
   ^String key-password
   ^String pkey-file
   ^String cert-chain]
  (try
    (when-let [chain (and pkey-file
                          cert-chain
                          (load-chain cert-chain))]
      (let [^PrivateKey key (las/private-key pkey-file)]
        {:keystore (doto (KeyStore/getInstance
                          (KeyStore/getDefaultType))
                     (.load nil nil)
                     (.setKeyEntry key-alias
                                   key
                                   (char-array key-password)
                                   chain))
         :private-key (slurp
                       (.getEncoded key))}))
    (catch java.io.FileNotFoundException _
      nil)))

(defn- file->keystore
  "Return a `java.security.KeyStore` instance from `filepath`, protected
   by the keystore `password`.
  If no file is found, return nil."
  [filepath password]
  (try
    (let [istream (input-stream filepath)
          pass    (char-array password)]
      (doto (ks/*get-instance (ks/*get-default-type))
        (ks/load istream pass)))
    (catch java.io.FileNotFoundException _
      nil)))

(defn- keystore->private-key
  "Return a string representation of the private key stored in `keystore`
   denoted by `alias` and protected by `password`."
  [keystore alias password]
  (-> keystore
      (ks/get-key alias (char-array password))
      k/get-encoded
      slurp))

(defn init-keystore
  "Initialize the keystore, either from file or other means.
  Return the keystore and the private key for use in signing"
  [{:keys [key-file
           key-alias
           key-password
           key-pkey-file
           key-cert-chain
           key-enable-selfie]
    :as config}]
  (or
   (and key-file
        (when-let [keystore (file->keystore key-file key-password)]
          (log/infof "Keystore file found at %s" key-file)
          {:keystore keystore
           :private-key (keystore->private-key keystore key-alias key-password)}))
   (and key-pkey-file
        key-cert-chain
        (when-let [result (cert-keystore
                           key-alias
                           key-password
                           key-pkey-file
                           key-cert-chain)]
          (log/info "Generated keystore from key and cert(s)...")
          result))
   (when key-enable-selfie
     (log/warn "No cert files found. Creating self-signed cert!")
     (selfie-keystore
      key-alias key-password))
   (throw (ex-info "Cannot Create Keystore"
                   {:type ::cannot-create-keystore
                    :config config}))))
