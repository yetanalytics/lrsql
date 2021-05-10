(ns test.lrsql.hugsql.input-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.test.alpha :as stest]
            [lrsql.hugsql.input :as input]))

;; Copied from training-commons.xapi.statement-gen-test
(defn- check-validate
  "Given the function name `fname`, returns `nil` if its generative
   tests passes, the erroneous result otherwise. If `num-tests` is
   not provided, runs 10 tests by default."
  ([fname]
   (check-validate fname 10))
  ([fname num-tests]
   (let [opts {:clojure.spec.test.check/opts
               {:num-tests num-tests
                :seed      (rand-int Integer/MAX_VALUE)}}
         res (stest/check fname opts)]
     (when-not (true? (-> res first :clojure.spec.test.check/ret :pass?))
       res))))

(deftest test-insert-inputs
  (testing "statement insertion inputs"
    (is (nil? (check-validate `input/agent->insert-input)))
    (is (nil? (check-validate `input/activity->insert-input)))
    (is (nil? (check-validate `input/attachment->insert-input)))
    (is (nil? (check-validate `input/agent-input->link-input)))
    (is (nil? (check-validate `input/activity-input->link-input)))
    (is (nil? (check-validate `input/attachment-input->link-input)))
    (is (nil? (check-validate `input/statement->insert-input))))
  (testing "document insertion inputs"
    (is (nil? (check-validate `input/document->insert-input)))))

(deftest test-query-inputs
  (testing "statement query inputs"
    (is (nil? (check-validate `input/params->query-input 100)))))
