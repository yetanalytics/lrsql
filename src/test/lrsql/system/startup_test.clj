(ns lrsql.system.startup-test
  "Tests that a failed startup is logged as one line without configuration
   values and exits with status 1, and that a valid startup is unchanged."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.system.util :as su])
  (:import [ch.qos.logback.classic Logger]
           [ch.qos.logback.classic.spi ILoggingEvent ThrowableProxyUtil]
           [ch.qos.logback.core.read ListAppender]
           [java.io ByteArrayOutputStream PrintStream StringWriter]
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

(defn- capture
  "Run `f` with a ListAppender on the root logger, with stdout and stderr
   captured and `exit!` recording its status instead of exiting. Returns
   {:result :events :text :exits}; :text is every event, with its throwable,
   and everything printed."
  [f]
  (let [^Logger root      (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME)
        ^ListAppender app (doto (ListAppender.) (.start))
        exits             (atom [])
        sys-out           System/out
        sys-err           System/err
        out-bytes         (ByteArrayOutputStream.)
        err-bytes         (ByteArrayOutputStream.)
        out-w             (StringWriter.)
        err-w             (StringWriter.)]
    (.addAppender root app)
    (System/setOut (PrintStream. out-bytes true "UTF-8"))
    (System/setErr (PrintStream. err-bytes true "UTF-8"))
    (try
      (let [result (binding [*out* out-w *err* err-w]
                     (with-redefs-fn {#'su/exit! (fn [status] (swap! exits conj status) nil)}
                       f))
            events (vec (.-list app))]
        {:result result
         :events events
         :exits  @exits
         :text   (cstr/join "\n" (concat (map event-text events)
                                         [(.toString out-bytes "UTF-8")
                                          (.toString err-bytes "UTF-8")
                                          (str out-w) (str err-w)]))})
      (finally
        (System/setOut sys-out)
        (System/setErr sys-err)
        (.detachAppender root app)))))

(defn- startup-lines
  [{:keys [events]}]
  (->> events
       (filter #(= su/startup-logger-name (.getLoggerName ^ILoggingEvent %)))
       (map (fn [^ILoggingEvent e] [(str (.getLevel e)) (.getFormattedMessage e)]))))

(defn- planted
  "Config overrides that plant the markers in every secret setting the web
   server and LRS read, plus `more`."
  [ms more]
  (merge {[:webserver :jwt-common-secret] (:jwt-common-secret ms)
          [:webserver :key-password]      (:key-password ms)
          [:lrs :admin-pass-default]      (:admin-pass-default ms)
          [:lrs :api-key-default]         (:api-key-default ms)
          [:lrs :api-secret-default]      (:api-secret-default ms)}
         more))

(defn- db-type []
  (get-in (support/test-system) [:connection :config :database :db-type]))

(defn- run-failing-start
  [overrides]
  (capture #(su/start-system-or-exit
             (fn [] (support/test-system :conf-overrides overrides)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest leak-checker-control-test
  (testing "the capture sees a logged throwable's data and both console streams"
    (let [ms  (markers)
          run (capture (fn []
                         (.error (LoggerFactory/getLogger "lrsql.startup-test")
                                 "planted"
                                 (ex-info "boom" {:secret (:key-password ms)}))
                         (.println System/err (str (:db-password ms)))
                         (println (:api-secret-default ms))))]
      (is (pos? (:key-password (leak-counts (:text run) ms))))
      (is (pos? (:db-password (leak-counts (:text run) ms))))
      (is (pos? (:api-secret-default (leak-counts (:text run) ms)))))))

(deftest startup-failure-line-test
  (let [ms   (markers)
        err  (RuntimeException.
              "outer"
              (ex-info "Inner failure\nsecond line"
                       {:type :some/type :system-key :webserver
                        :config {:secret (:jwt-common-secret ms)}}))
        line (su/startup-failure-line err)]
    (is (= (str "Startup failed in component :webserver: java.lang.RuntimeException: outer"
                " <- clojure.lang.ExceptionInfo type=:some/type: Inner failure second line")
           line))
    (is (no-leak? line ms))
    (testing "a :type that is not a keyword is left out"
      (is (= "Startup failed: clojure.lang.ExceptionInfo: x"
             (su/startup-failure-line (ex-info "x" {:type (:jwt-common-secret ms)})))))))

(deftest keystore-startup-test
  (let [ms  (markers)
        run (run-failing-start
             ;; The test profile sets no PEM key or chain.
             (planted ms {[:webserver :key-file]          "/nonexistent/keystore.jks"
                          [:webserver :key-enable-selfie] false}))
        [[level line] :as lines] (startup-lines run)]
    (testing "startup exits with status 1"
      (is (= [1] (:exits run)))
      (is (nil? (:result run))))
    (testing "no configured secret is logged or printed"
      (is (no-leak? (:text run) ms)))
    (testing "one ERROR line says what failed"
      (is (= 1 (count lines)))
      (is (= "ERROR" level))
      (is (cstr/includes? line "Cannot Create Keystore"))
      (is (cstr/includes? line "in component :webserver"))
      (is (cstr/includes? line "type=:lrsql.util.cert/cannot-create-keystore")))))

(deftest invalid-setting-startup-test
  ;; Only the startup line is checked for secrets here: the configuration
  ;; check's own log line is a separate concern.
  (let [ms  (markers)
        run (run-failing-start (planted ms {[:lrs :stmt-url-prefix] "xapi"}))
        [[level line] :as lines] (startup-lines run)]
    (is (= [1] (:exits run)))
    (is (= 1 (count lines)))
    (is (= "ERROR" level))
    (is (no-leak? line ms))
    (is (cstr/includes? line "in component :lrs"))
    (is (cstr/includes? line "Invalid LRS configuration!"))
    (is (cstr/includes? line "type=:lrsql.system.util/invalid-config"))))

(deftest database-unreachable-startup-test
  (let [ms   (markers)
        db   (db-type)
        more (if (= "sqlite" db)
               ;; A file in a directory that does not exist cannot be opened.
               {[:connection :database :db-name]     "/nonexistent-dir/lrsql.sqlite.db"
                [:connection :database :db-password] (:db-password ms)}
               (cond-> {[:connection :database :db-host]     "unreachable.invalid"
                        [:connection :database :db-password] (:db-password ms)}
                 (= "postgres" db)
                 (assoc [:connection :database :db-properties]
                        (str "ApplicationName=" (:db-properties ms)))))
        run  (run-failing-start (planted ms more))
        [[level line] :as lines] (startup-lines run)]
    (is (= [1] (:exits run)))
    (is (no-leak? (:text run) ms))
    (is (= 1 (count lines)))
    (is (= "ERROR" level))
    ;; Depending on the backend's pool settings, the unreachable database
    ;; fails either the connection itself or its first use, by the LRS.
    (is (re-find #"in component :(connection|lrs):" line))))

(deftest valid-startup-unchanged-test
  (let [run (capture #(su/start-system-or-exit support/test-system))
        sys (:result run)]
    (try
      (is (= [] (:exits run)))
      (is (empty? (startup-lines run)))
      (is (some? (get-in sys [:webserver :server])))
      (finally
        (when sys (component/stop sys))))))
