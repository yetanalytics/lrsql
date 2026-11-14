(ns lrsql.util.crypto-mode-test
  "Tests that every launcher's startup path logs the JVM's crypto mode. This
   namespace uses only `lrsql.system.util/start-system-or-exit`, which all
   three `-main` functions call, and the logger name."
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.system.util :as su])
  (:import [ch.qos.logback.classic Level Logger]
           [ch.qos.logback.classic.spi ILoggingEvent]
           [ch.qos.logback.core.read ListAppender]
           [java.security Provider Security]
           [javax.crypto Mac]
           [org.slf4j LoggerFactory]))

(defn- capture-crypto-lines
  "Run `f` with a ListAppender on the `lrsql.crypto` logger at INFO (the
   test configuration logs WARN and above) and `exit!` recording its status
   instead of exiting. Return the crypto lines as `[level message]` and the
   exit statuses."
  [f]
  (let [^Logger logger    (LoggerFactory/getLogger "lrsql.crypto")
        ^ListAppender app (doto (ListAppender.) (.start))
        exits             (atom [])
        level             (.getLevel logger)]
    (.setLevel logger Level/INFO)
    (.addAppender logger app)
    (try
      (with-redefs-fn {#'su/exit! (fn [status] (swap! exits conj status) nil)}
        f)
      {:lines (mapv (fn [^ILoggingEvent e]
                      [(str (.getLevel e)) (.getFormattedMessage e)])
                    (.-list app))
       :exits @exits}
      (finally
        (.detachAppender logger app)
        (.setLevel logger level)))))

(deftest crypto-mode-startup-line-test
  (let [{:keys [lines exits]}
        (capture-crypto-lines
         #(su/start-system-or-exit
           (fn [] (throw (ex-info "startup stopped by the test" {})))))
        [[level msg]] lines
        first-p (.getName ^Provider (first (Security/getProviders)))
        mac-p   (.getName (.getProvider (Mac/getInstance "HmacSHA256")))]
    (testing "the startup path logs one crypto mode line, even when startup fails"
      (is (= 1 (count lines)))
      (is (= "INFO" level))
      (is (= [1] exits)))
    (testing "the line describes this JVM"
      (is (some? msg))
      (when msg
        (is (re-find #"^Crypto mode: fips=(true|false) " msg))
        (is (re-find (re-pattern (str " first-provider="
                                      (java.util.regex.Pattern/quote first-p)
                                      " "))
                     msg))
        (is (re-find #" com\.redhat\.fips=\S+ " msg))
        (is (re-find #" kernel-fips_enabled=\S+ " msg))
        (is (re-find (re-pattern (str " Mac[.]HmacSHA256="
                                      (java.util.regex.Pattern/quote mac-p)
                                      " "))
                     msg))
        (is (re-find #" SecureRandom=\S+/\S+$" msg))
        (testing "fips=true only with a FIPS provider first"
          (is (= (boolean (re-find #"FIPS" first-p))
                 (boolean (re-find #"fips=true" msg)))))))))
