(ns lrsql.util.concurrency
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [next.jdbc :refer [with-transaction]]))

;; `j-range` and `initial` are internal opts and should not be set by the user.
;; We also need to enforce MAX_VALUE constraints since `int?`, `nat-int?`, etc
;; don't do so on their own.

(s/def ::budget      (s/and int? (fn [n] (< 0 n Integer/MAX_VALUE))))
(s/def ::max-attempt (s/and int? (fn [n] (< 0 n Integer/MAX_VALUE))))
(s/def ::j-range     (s/and int? (fn [n] (<= 0 n Integer/MAX_VALUE))))
(s/def ::initial     (s/and int? (fn [n] (<= 0 n Integer/MAX_VALUE))))

(def backoff-opts-spec
  (s/keys :req-un [::budget ::max-attempt]
          :opt-un [::j-range ::initial]))

(s/fdef backoff-ms
  :args (s/cat :attempt nat-int?
               :opts backoff-opts-spec)
  :ret (s/nilable nat-int?))

(defn backoff-ms
  "Take an `attempt` number and an opts map containing the total `:budget`
   in ms and an `:max-attempt` number and return a backoff time in ms.
   Can also optionally provide a jitter in `j-range` ms and an `initial` ms
   amount of delay to be used first in the opts map."
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
        ;; Type hinted to long for JDK16+ which won't accept it without
        (let [^long sleep (backoff-ms (inc attempt) opts)]
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
    (fn [] (transact ~transactable ~f ~opts))
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
