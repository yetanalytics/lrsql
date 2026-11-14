(ns lrsql.util.cert-error-test
  "Tests that the \"Cannot Create Keystore\" error carries no configuration
   value, so a keystore failure at startup prints no secret."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [lrsql.test-support :as support]
            [lrsql.system.util :as su]
            [lrsql.util.cert :as cert])
  (:import [java.io PrintWriter StringWriter]
           [java.security SecureRandom]
           [java.util.regex Pattern]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(defn- marker
  "A random 40-character value that will not occur in output by chance."
  []
  (let [r (SecureRandom.)
        a "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"]
    (apply str (repeatedly 40 #(.charAt a (.nextInt r 62))))))

(defn- markers
  []
  {:jwt-common-secret  (marker)
   :key-password       (marker)
   :admin-pass-default (marker)
   :api-key-default    (marker)
   :api-secret-default (marker)})

(defn- leak-counts
  "How many times each marker occurs in `text`."
  [text ms]
  (into {} (map (fn [[k m]] [k (count (re-seq (re-pattern (Pattern/quote m)) text))]))
        ms))

(defn- no-leak? [text ms] (every? zero? (vals (leak-counts text ms))))

(defn- printed
  "`ex` with its stack trace and causes, as the JVM prints an uncaught
   exception."
  [^Throwable ex]
  (let [w (StringWriter.)]
    (.printStackTrace ex (PrintWriter. w))
    (str w)))

(deftest leak-checker-control-test
  (testing "the checker sees a marker in an ex-info printed with its data"
    (let [ms   (markers)
          text (printed (ex-info "Cannot Create Keystore"
                                 {:config {:jwt-common-secret
                                           (:jwt-common-secret ms)}}))]
      (is (= 1 (:jwt-common-secret (leak-counts text ms))))
      (is (not (no-leak? text ms))))))

(deftest keystore-error-data-test
  (let [ms (markers)
        ex (try (cert/init-keystore {:key-file          "/nonexistent/keystore.jks"
                                     :key-alias         "lrsql_keystore"
                                     :key-password      (:key-password ms)
                                     :key-pkey-file     nil
                                     :key-cert-chain    nil
                                     :key-enable-selfie false
                                     :jwt-common-secret (:jwt-common-secret ms)})
                nil
                (catch clojure.lang.ExceptionInfo e e))
        data (ex-data ex)]
    (testing "the message and type are unchanged"
      (is (= "Cannot Create Keystore" (ex-message ex)))
      (is (= ::cert/cannot-create-keystore (:type data))))
    (testing "the data has no :config and no configured secret"
      (is (not (contains? data :config)))
      (is (not-any? (set (vals ms)) (tree-seq coll? seq data)))
      (is (no-leak? (pr-str data) ms))
      (is (no-leak? (printed ex) ms)))
    (testing "the data says which sources were set"
      (is (= {:key-file-set       true
              :key-pkey-file-set  false
              :key-cert-chain-set false
              :key-enable-selfie  false}
             (dissoc data :type))))))

(deftest keystore-startup-exception-test
  (testing "a keystore failure at startup, printed as uncaught, has no secret"
    (let [ms  (markers)
          ex  (try (su/start-system
                    (support/test-system
                     :conf-overrides
                     ;; The test profile sets no PEM key or chain.
                     {[:webserver :key-file]           "/nonexistent/keystore.jks"
                      [:webserver :key-enable-selfie]  false
                      [:webserver :jwt-common-secret]  (:jwt-common-secret ms)
                      [:webserver :key-password]       (:key-password ms)
                      [:lrs :admin-pass-default]       (:admin-pass-default ms)
                      [:lrs :api-key-default]          (:api-key-default ms)
                      [:lrs :api-secret-default]       (:api-secret-default ms)}))
                   nil
                   (catch Throwable t t))
          text (some-> ex printed)]
      (is (some? ex))
      (is (cstr/includes? (str text) "Cannot Create Keystore"))
      (is (= {:jwt-common-secret 0 :key-password 0 :admin-pass-default 0
              :api-key-default 0 :api-secret-default 0}
             (leak-counts (str text) ms))))))
