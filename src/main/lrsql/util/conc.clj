(ns lrsql.util
  (:require [clojure.tools.logging :as log]))

(defn backoff-ms
  "Take an overall time budget in ms, an attempt number, max attempts and
  return a backoff time in ms. Can optionally provide range for jitter and
  an initial delay to be used first"
  [budget attempt max-attempt & {:keys [j-range initial]
                                 :or {j-range 10}}]
  (let [jitter (rand-int j-range)]
    (cond
      (= attempt 0)           0
      (> attempt max-attempt) nil
      (and (some? initial)
           (= attempt 1))     (+ initial jitter)
      :else                   (int (+ (* budget (/ (Math/pow 2 (- attempt 1))
                                                   (Math/pow 2 max-attempt)))
                                      jitter)))))

(defn rerunable-txn*
  [txn budget max-attempt attempt]
  (try
    (txn)
    ;;TODO: make specific protocol fn for each sql that examines exc and allows retry
    (catch org.postgresql.util.PSQLException e
      (do
        (log/info (str "had error on attempt " attempt))
        (if (< attempt max-attempt)
          (do
            (log/info "trying again")
            (let [sleep (backoff-ms budget (+ 1 attempt) max-attempt)]
              (log/info (str "sleeping for " sleep))
              (Thread/sleep sleep))
            (log/info "awake!")
            (rerunable-txn* txn budget max-attempt (+ 1 attempt)))
          (do
            (log/info "out of attempts")
            (throw e)))))))


(defn rerunable-txn
  [txn budget max-attempt]
  (rerunable-txn* txn budget max-attempt 0))
