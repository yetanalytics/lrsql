(ns lrsql.util.cert
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
    X509Certificate]))

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

(defn selfie-key-store
  "Create a key store + key pair with a self-signed cert"
  ^KeyStore
  [^String key-alias
   ^String key-password
   & selfie-args]
  (let [{:keys
         [^X509CertImpl cert
          ^KeyPair key-pair]
         :as selfie} (apply selfie selfie-args)]
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
