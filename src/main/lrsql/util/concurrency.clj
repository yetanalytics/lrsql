(ns lrsql.util.concurrency
  (:require [clojure.tools.logging :as log]
            [next.jdbc :refer [with-transaction]]))

;; `j-range` and `initial` are internal opts and should not be set by the user.

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
  "Take a `txn-expr` thunk, a positive number of `attempt`s, and an `opts` map,
   and run `txn-expr`. If it throws an exception and `:retry-test` passes,
   attempt to retry until `:max-attempt` has been reached, with the backoff
   time exponentially increasing and scaled by `:budget`."
  [txn-expr attempt {:keys [retry-test max-attempt] :as opts}]
  (try
    (txn-expr)
    (catch Exception e
      (if (and (< attempt max-attempt)
               (retry-test e))
        (let [sleep (apply backoff-ms (inc attempt) opts)]
          (Thread/sleep sleep)
          (rerunable-txn* txn-expr (inc attempt) opts))
        (do
          (log/warn "Rerunable Transaction exhausted attempts or could not be retried")
          (throw e))))))

(defmacro rerunable-txn
  "Macro to create a rerunable version of `next.jdbc/transact`. `f` needs to
   be a one-arity function that takes a transaction arg and runs the body.
   `opts` include `:retry-test`, `:max-attempt`, and `:budget`, as well as the
   usual options for `transact`."
  [transactable f opts]
  `(rerunable-txn*
    (fn [] (transact ~transactable ~f (not-empty ~opts)))
    0
    ~opts))

(defmacro with-rerunable-txn
  "Macro to create a rerunable version of `next.jdbc/with-transaction`. Binds
   `sym` to `transactable` and executes `body` in a rerunable manner. `opts`
   include `:retry-test`, `:max-attempt`, and `:budget`, as well as the usual
   options for `with-transaction`."
  [[sym transactable opts] & body]
  `(rerunable-txn*
    (fn [] (with-transaction [~sym ~transactable ~opts] ~@body))
    0
    ~opts))
