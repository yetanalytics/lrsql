(ns lrsql.util.cert
  (:require
   [jdk.security.KeyStore :as ks]
   [jdk.security.Key :as k]
   [clojure.java.io :refer [input-stream]]
   [clojure.string :as cs]
   [less.awful.ssl :as las]
   [clojure.tools.logging :as log])
  (:import
   [sun.security.x509
    X509CertImpl
    CertificateAlgorithmId
    AlgorithmId
    CertificateVersion
    CertificateX509Key
    CertificateSerialNumber
    X500Name
    CertificateValidity
    X509CertInfo]
   [java.security
    KeyStore
    KeyPairGenerator
    SecureRandom
    PrivateKey
    KeyPair]
   [java.security.cert
    Certificate
    X509Certificate]
   [java.io File]))

;; a la https://stackoverflow.com/a/44738069/3532563
(defn selfie
  "Return a self-signed cert & KeyPair"
  [& {:keys [^String dn
             ^Long days
             ^String alg]
      :or {dn "CN=com.yetanalytics.lrsql, OU=Dev, O=Yet Analytics, L=Baltimore, S=Maryland, C=US"
           days 365000 ;; 1000 years of darkness
           alg "SHA256withRSA"}}]
  (let [;; gen a pair
        ^KeyPairGenerator kpg (KeyPairGenerator/getInstance "RSA")
        ^KeyPair key-pair
        (.generateKeyPair kpg)

        ^PrivateKey private-key (.getPrivate key-pair)
        ^java.util.Date from (new java.util.Date)
        ^java.util.Date to (new java.util.Date
                                (-> from
                                    .getTime
                                    (+ (* days 86400000))))
        ^CertificateValidity interval (new CertificateValidity
                                           from to)
        ^BigInteger sn (new BigInteger
                            64
                            (new SecureRandom))
        ^X500Name owner (new X500Name dn)
        ^AlgorithmId algo (new AlgorithmId
                               AlgorithmId/md5WithRSAEncryption_oid)
        ^X509CertInfo info
        (doto (new X509CertInfo)
          (.set X509CertInfo/VALIDITY interval)
          (.set X509CertInfo/SERIAL_NUMBER
                (new CertificateSerialNumber sn))
          (.set X509CertInfo/SUBJECT owner)
          (.set X509CertInfo/ISSUER owner)
          (.set X509CertInfo/KEY (new CertificateX509Key
                                      (.getPublic key-pair)))
          (.set X509CertInfo/VERSION
                (new CertificateVersion
                     CertificateVersion/V3))
          (.set X509CertInfo/ALGORITHM_ID (new CertificateAlgorithmId
                                               algo)))

        ^X509CertImpl cert (doto (new X509CertImpl info)
                             ;; Sign the cert to identify the algorithm
                             (.sign private-key alg))
        ;; Update the algorithm, and re-sign.
        ^AlgorithmId algo-final (.get cert X509CertImpl/SIG_ALG)
        ;; mutation of the info carried from example :()
        _ (.set info
                (format
                 "%s.%s"
                 CertificateAlgorithmId/NAME
                 CertificateAlgorithmId/ALGORITHM)
                algo-final)

        ^X509CertImpl cert-final (doto (new X509CertImpl info)
                                   (.sign private-key alg))
        ]
    {:cert cert-final
     :key-pair key-pair}))

(defn selfie-keystore
  "Create a key store + private key with a self-signed cert"
  ^KeyStore
  [^String key-alias
   ^String key-password
   & selfie-args]
  (let [{:keys
         [^X509CertImpl cert
          ^KeyPair key-pair]} (apply selfie selfie-args)]
    {:keystore (doto (KeyStore/getInstance
                      (KeyStore/getDefaultType))
                 (.load nil nil)
                 (.setKeyEntry key-alias
                               (.getPrivate key-pair)
                               (char-array key-password)
                               (into-array Certificate [cert])))
     :private-key
     (slurp
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
      (println 'chain chain)
      (let [^PrivateKey key (las/private-key pkey-file)]
        {:keystore (doto (KeyStore/getInstance
                          (KeyStore/getDefaultType))
                     (.load nil nil)
                     (.setKeyEntry key-alias
                                   key
                                   (char-array key-password)
                                   chain))
         :private-key
         (slurp
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
   (and (and key-pkey-file
             key-cert-chain)
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
