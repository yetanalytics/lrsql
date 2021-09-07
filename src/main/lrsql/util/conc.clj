(ns lrsql.util.conc
  (:require [clojure.tools.logging :as log]
            [next.jdbc :refer [with-transaction]]))

(defn backoff-ms
  "Take an overall time budget in ms, an attempt number, max attempts and
  return a backoff time in ms. Can optionally provide range for jitter and
  an initial delay to be used first"
  [attempt {:keys [budget max-attempt j-range initial]
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
  [txn-expr attempt {:keys [retry-test max-attempt] :as opts}]
  (try
    (txn-expr)
    (catch Exception e
      (if (and (< attempt max-attempt)
               (retry-test e))
        (let [sleep (apply backoff-ms (inc attempt) opts)]
          (Thread/sleep sleep)
          (apply rerunable-txn* txn-expr retry-test (inc attempt) opts))
        (do
          (log/warn "Rerunable Transaction exhausted attempts or could not be retried")
          (throw e))))))

(defmacro rerunable-txn
  [transactable f opts]
  `(rerunable-txn*
    (transact ~transactable ~f ~(not-empty opts))
    0
    (select-keys ~opts [:max-attempt
                        :retry-test
                        :budget
                        :j-range
                        :inital])))

(defmacro with-rerunable-txn
  [[sym transactable opts] & body]
  `(rerunable-txn*
    (with-transaction [~sym ~transactable ~opts] ~@body)
    0
    (select-keys ~opts [:max-attempt
                        :retry-test
                        :budget
                        :j-range
                        :initial])))
