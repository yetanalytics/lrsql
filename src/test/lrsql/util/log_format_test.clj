(ns lrsql.util.log-format-test
  "Tests for the log line format that `src/main/logback.xml` configures."
  (:require [clojure.java.io :as io]
            [clojure.test    :refer [deftest is testing]]
            [clojure.xml     :as xml])
  (:import [ch.qos.logback.classic Level LoggerContext PatternLayout]
           [ch.qos.logback.classic.spi LoggingEvent]
           [java.time Instant]
           [java.util TimeZone]))

(defn- encoder-patterns
  "Return every encoder `<pattern>` in the production `logback.xml`."
  []
  (with-open [in (io/input-stream (io/resource "logback.xml"))]
    (->> (xml-seq (xml/parse in))
         (filter #(= :pattern (:tag %)))
         (mapv (comp first :content)))))

(defn- format-event
  "Format one INFO event logged at `instant` with `pattern`."
  [pattern ^Instant instant]
  (let [ctx    (LoggerContext.)
        layout (doto (PatternLayout.)
                 (.setContext ctx)
                 (.setPattern pattern)
                 (.start))
        logger (.getLogger ctx "lrsql.log-format-test")
        event  (doto (LoggingEvent. "lrsql.log-format-test"
                                    logger
                                    Level/INFO
                                    "log format marker"
                                    nil
                                    nil)
                 (.setTimeStamp (.toEpochMilli instant)))]
    (.doLayout layout event)))

(deftest log-line-date-test
  (let [patterns     (encoder-patterns)
        instant      (Instant/parse "2026-01-02T03:04:05.678Z")
        default-zone (TimeZone/getDefault)]
    (testing "both appenders (file and console) are covered"
      (is (= 2 (count patterns))))
    ;; A default zone away from UTC, and on the previous calendar day at this
    ;; instant, shows that the timestamp does not follow the host's zone.
    (TimeZone/setDefault (TimeZone/getTimeZone "America/Los_Angeles"))
    (try
      (doseq [pattern patterns]
        (testing (str "pattern " pattern)
          (let [line (format-event pattern instant)]
            (testing "starts with the full ISO 8601 date and time in UTC"
              (is (.startsWith ^String line "2026-01-02T03:04:05.678Z ")))
            (testing "keeps the rest of the line"
              (is (re-find #" INFO  lrsql\.log-format-test - log format marker"
                           line))))))
      (finally
        (TimeZone/setDefault default-zone)))))
