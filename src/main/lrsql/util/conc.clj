(ns lrsql.util.conc
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
  [txn retry-test budget max-attempt attempt & kwargs]
  (try
    (txn)
    (catch Exception e
      (if (and (< attempt max-attempt)
               (retry-test e))
        (let [sleep (apply backoff-ms
                           budget (+ 1 attempt) max-attempt kwargs)]
          (Thread/sleep sleep)
          (apply rerunable-txn*
                 txn retry-test budget max-attempt (+ 1 attempt) kwargs))
        (do
          (log/warn "Rerunable Transaction exhausted attempts or could not be retried")
          (throw e))))))


(defn rerunable-txn
  [txn retry-test budget max-attempt & kwargs]
  (apply rerunable-txn* txn retry-test budget max-attempt 0 kwargs))
