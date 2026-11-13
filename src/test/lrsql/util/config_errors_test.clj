(ns lrsql.util.config-errors-test
  "Tests that a rejected configuration, the connection's debug log line and an
   unreadable config file are reported without any configuration value, so no
   configured secret reaches the log, the console or exception data."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [com.stuartsierra.component :as component]
            [lrsql.init.config]
            [lrsql.system.database :as db]
            [lrsql.system.util :as su]
            [lrsql.test-support :as support])
  (:import [ch.qos.logback.classic Level Logger]
           [ch.qos.logback.classic.spi ILoggingEvent ThrowableProxyUtil]
           [ch.qos.logback.core.read ListAppender]
           [java.io ByteArrayOutputStream File PrintStream]
           [java.security SecureRandom]
           [java.util.regex Pattern]
           [org.slf4j LoggerFactory]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markers and capture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- marker
  "A random 40-character value that will not occur in a log by chance."
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
   :api-secret-default (marker)
   :db-password        (marker)
   :db-properties      (marker)})

(defn- leak-counts
  "How many times each marker occurs in `text`."
  [text ms]
  (into {} (map (fn [[k m]] [k (count (re-seq (re-pattern (Pattern/quote m)) text))]))
        ms))

(defn- no-leak? [text ms] (every? zero? (vals (leak-counts text ms))))

(defn- event-text
  [^ILoggingEvent e]
  (str (.getLoggerName e) " " (.getLevel e) " " (.getFormattedMessage e)
       (when-let [tp (.getThrowableProxy e)]
         (str "\n" (ThrowableProxyUtil/asString tp)))))

(defn- exception-text
  "Every exception in the cause chain of `ex`, printed with its data, as an
   uncaught exception would be."
  [^Throwable ex]
  (->> (iterate #(.getCause ^Throwable %) ex)
       (take-while some?)
       (map (fn [^Throwable t] (str (class t) ": " (.getMessage t) " "
                                    (pr-str (ex-data t)))))
       (cstr/join "\n")))

(defn- capture
  "Run `f` with a ListAppender on the root logger and stdout and stderr
   captured. Returns {:result :thrown :events :text}; :text is every event,
   with its throwable, everything printed, and the thrown exception's chain."
  [f]
  (let [^Logger root      (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME)
        ^ListAppender app (doto (ListAppender.) (.start))
        sys-out           System/out
        sys-err           System/err
        out-bytes         (ByteArrayOutputStream.)
        err-bytes         (ByteArrayOutputStream.)]
    (.addAppender root app)
    (System/setOut (PrintStream. out-bytes true "UTF-8"))
    (System/setErr (PrintStream. err-bytes true "UTF-8"))
    (try
      (let [[result thrown] (try [(f) nil]
                                 (catch Throwable t [nil t]))
            events          (vec (.-list app))]
        {:result result
         :thrown thrown
         :events events
         :text   (cstr/join "\n" (concat (map event-text events)
                                         [(.toString out-bytes "UTF-8")
                                          (.toString err-bytes "UTF-8")
                                          (some-> thrown exception-text)]))})
      (finally
        (System/setOut sys-out)
        (System/setErr sys-err)
        (.detachAppender root app)))))

(defn- planted
  "Config overrides that plant the markers in the web server and LRS secret
   settings, plus `more`. The database password is planted only where the
   test fails before connecting, since a real database would refuse it."
  [ms more]
  (merge {[:webserver :jwt-common-secret]      (:jwt-common-secret ms)
          [:webserver :key-password]           (:key-password ms)
          [:lrs :admin-pass-default]           (:admin-pass-default ms)
          [:lrs :api-key-default]              (:api-key-default ms)
          [:lrs :api-secret-default]           (:api-secret-default ms)}
         more))

(defn- start-invalid
  "Start a test system with the markers planted and `more` overrides, which
   must make it fail."
  [ms more]
  (capture #(su/start-system
             (support/test-system :conf-overrides (planted ms more)))))

(defn- root-cause [^Throwable t]
  (last (take-while some? (iterate #(.getCause ^Throwable %) t))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest leak-checker-control-test
  (testing "the checker sees a marker in a logged throwable's data and on the console"
    (let [ms  (markers)
          run (capture (fn []
                         (.error (LoggerFactory/getLogger "lrsql.config-errors-test")
                                 "planted"
                                 (ex-info "boom" {:secret (:key-password ms)}))
                         (.println System/err (str (:db-password ms)))
                         (throw (ex-info "thrown" {:secret (:api-secret-default ms)}))))]
      (is (pos? (:key-password (leak-counts (:text run) ms))))
      (is (pos? (:db-password (leak-counts (:text run) ms))))
      (is (pos? (:api-secret-default (leak-counts (:text run) ms))))
      (is (not (no-leak? (:text run) ms))))))

(deftest invalid-lrs-config-test
  (let [ms  (markers)
        ;; Fails the map-level check that the reaction version is supported,
        ;; so the whole LRS map is the failing value.
        run (start-invalid ms {[:lrs :reaction-version]   "2.0.0"
                               [:lrs :supported-versions] "1.0.3"})
        ex  (root-cause (:thrown run))]
    (testing "startup still fails with the same message and type"
      (is (= "Invalid LRS configuration!" (ex-message ex)))
      (is (= ::su/invalid-config (:type (ex-data ex)))))
    (testing "no configured secret is logged, printed or in exception data"
      (is (= {:jwt-common-secret 0 :key-password 0 :admin-pass-default 0
              :api-key-default 0 :api-secret-default 0 :db-password 0
              :db-properties 0}
             (leak-counts (:text run) ms))))
    (testing "the log still names the failed check"
      (is (cstr/includes? (:text run) "Configuration errors"))
      (is (cstr/includes? (:text run) "reaction-version-supported?")))))

(deftest invalid-webserver-config-test
  (let [ms  (markers)
        ;; Fails the map-level check that the refresh interval is shorter
        ;; than the token lifetime.
        run (start-invalid ms {[:webserver :jwt-exp-time]         3600
                               [:webserver :jwt-refresh-interval] 7200})
        ex  (root-cause (:thrown run))]
    (is (= "Invalid webserver configuration!" (ex-message ex)))
    (is (= ::su/invalid-config (:type (ex-data ex))))
    (is (no-leak? (:text run) ms))
    (is (cstr/includes? (:text run) "jwt-refresh-interval"))))

(deftest invalid-connection-config-test
  (let [ms  (markers)
        ;; Fails the map-level check that the validation timeout is shorter
        ;; than the connection timeout; checked before any connection.
        run (start-invalid ms {[:connection :database :db-password] (:db-password ms)
                               [:connection :database :db-properties]
                               (str "ApplicationName=" (:db-properties ms))
                               [:connection :pool-validation-timeout] 5000
                               [:connection :pool-connection-timeout] 4000})
        ex  (root-cause (:thrown run))]
    (is (= "Invalid connection configuration!" (ex-message ex)))
    (is (= ::su/invalid-config (:type (ex-data ex))))
    (is (no-leak? (:text run) ms))
    (is (cstr/includes? (:text run) "validation-lt-connection-timeout?"))))

(deftest connection-debug-line-test
  (let [ms     (markers)
        config (-> (get-in (support/test-system) [:connection :config])
                   (assoc-in [:database :db-password] (:db-password ms))
                   (assoc-in [:database :db-properties]
                             (str "ApplicationName=" (:db-properties ms))))
        ^Logger logger (LoggerFactory/getLogger "lrsql.system.database")
        level  (.getLevel logger)]
    (.setLevel logger Level/DEBUG)
    (try
      (let [run (capture
                 #(with-redefs-fn {#'db/make-conn-pool (fn [_ _] nil)}
                    (fn []
                      (component/start (db/map->Connection {:config config})))))
            debug-lines (->> (:events run)
                             (filter #(= "lrsql.system.database"
                                         (.getLoggerName ^ILoggingEvent %)))
                             (filter #(= Level/DEBUG (.getLevel ^ILoggingEvent %)))
                             (map #(.getFormattedMessage ^ILoggingEvent %)))]
        (testing "the debug line is logged"
          (is (= 1 (count debug-lines))))
        (testing "it carries no configured value"
          (is (no-leak? (:text run) ms)))
        (testing "it still names the settings in use"
          (is (cstr/includes? (first debug-lines) ":db-properties"))))
      (finally
        (.setLevel logger level)))))

(deftest config-file-parse-error-test
  (let [ms   (markers)
        f    (File/createTempFile "lrsql-config" ".json")
        path (.getPath f)]
    (try
      ;; Unquoted values make the parser quote them in its message.
      (spit f (str "{\"webserver\": {\"jwtCommonSecret\": "
                   (:jwt-common-secret ms)
                   "},\n \"database\": {\"dbPassword\": "
                   (:db-password ms) "}}"))
      (let [run (capture #(#'lrsql.init.config/merge-user-config
                           {:user-config-json path}))
            ex  ^Throwable (:thrown run)
            data (ex-data ex)]
        (testing "the file is still refused with the same message and type"
          (is (= "Invalid JSON in Config File" (ex-message ex)))
          (is (= :lrsql.init.config/invalid-user-config-json (:type data)))
          (is (= path (:path data))))
        (testing "no value from the file is in the exception or its causes"
          (is (no-leak? (:text run) ms))
          (is (nil? (.getCause ex))))
        (testing "the error says where the file is wrong"
          (is (= 1 (:line data)))
          (is (pos-int? (:column data)))
          (is (string? (:error data)))))
      (finally
        (io/delete-file f true)))))
