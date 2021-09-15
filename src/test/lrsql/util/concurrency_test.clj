(ns lrsql.util.concurrency-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.util.concurrency :as conc]
            [lrsql.test-support :refer [check-validate]]))

(deftest backoff-calc-test
  (testing "backoff calculation"
    ;; Generative testing
    (testing "(gentest)"
      (is (nil? (check-validate `conc/backoff-ms 1000))))

    ;; Unit testing

    ;; 0th attempt should be zero backoff
    (is (= 0 (conc/backoff-ms 0 {:budget      10
                                 :max-attempt 10})))

    ;; First retry with a set initial and 0 jitter
    (is (= 5 (conc/backoff-ms 1 {:budget 1000
                                 :initial 5
                                 :max-attempt 10
                                 :j-range 0})))

    ;; Over max should return nil
    (is (nil? (conc/backoff-ms 10 {:budget 1000
                                   :max-attempt 5})))

    ;; Exact values no jitter, over 1000 budget
    (is (= (mapv #(conc/backoff-ms % {:budget 1000
                                      :j-range 0
                                      :max-attempt 5})
                 [0 1 2 3 4 5 6])
           [0 31 62 125 250 500 nil]))

    ;; Jitter included
    (is (let [value (conc/backoff-ms 3 {:budget 1000
                                        :max-attempt 5
                                        :j-range 5})]
          (and (>= value 125)
               (< value 131))))))

(deftest rerunable-txn-test
  (testing "rerunable-txn* function"
    (is (= 2
           (conc/rerunable-txn* (fn [] 2)
                                0
                                {:retry-test  (comp not int?)
                                 :max-attempt 10
                                 :budget      10})))
    (is (= 2 ; max-attempt has no effect if transaction never fails
           (conc/rerunable-txn* (fn [] 2)
                                0
                                {:retry-test  (comp not int?)
                                 :max-attempt 11
                                 :budget      10})))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (conc/rerunable-txn* (fn [] (throw (ex-info "Bad!" {:bad? true})))
                              0
                              {:retry-test  (fn [ex] (-> (ex-data ex) :bad?))
                               :max-attempt 10
                               :budget      10})))
    (testing "- `txn-expr` throws before `max-attempt` is reached"
      (is (= 2
             (let [ctr (atom 0)]
               (conc/rerunable-txn*
                (fn []
                  (if (< @ctr 10)
                    (do (swap! ctr inc)
                        (throw (ex-info "Bad!" {:bad? true})))
                    2))
                0
                {:retry-test  (comp not int?)
                 :max-attempt 10
                 :budget      10})))))
    (testing "- `txn-expr` throws even as `max-attempt` is reached"
      (is (thrown?
           clojure.lang.ExceptionInfo
           (let [ctr (atom 0)]
             (conc/rerunable-txn*
              (fn []
                (if (< @ctr 11) ; one more than max-attempt
                  (do (swap! ctr inc)
                      (throw (ex-info "Bad!" {:bad? true})))
                  2))
              0
              {:retry-test  (comp not int?)
               :max-attempt 10
               :budget      10})))))))
