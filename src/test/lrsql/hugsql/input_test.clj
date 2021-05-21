(ns lrsql.hugsql.input-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.test.alpha :as stest]
            [lrsql.hugsql.input.agent     :as agent-input]
            [lrsql.hugsql.input.activity  :as activity-input]
            [lrsql.hugsql.input.statement :as stmt-input]
            [lrsql.hugsql.input.document  :as doc-input]))

;; Copied from training-commons.xapi.statement-gen-test
(defn- check-validate
  "Given the function name `fname`, returns `nil` if its generative
   tests passes, the erroneous result otherwise. If `num-tests` is
   not provided, runs 50 tests by default."
  ([fname]
   (check-validate fname 50))
  ([fname num-tests]
   (let [opts {:clojure.spec.test.check/opts
               {:num-tests num-tests
                :seed      (rand-int Integer/MAX_VALUE)}}
         res (stest/check fname opts)]
     (when-not (true? (-> res first :clojure.spec.test.check/ret :pass?))
       res))))

(deftest test-insert-inputs
  (testing "statement object insertion inputs"
    (is (nil? (check-validate `stmt-input/actor-insert-input)))
    (is (nil? (check-validate `stmt-input/activity-insert-input)))
    (is (nil? (check-validate `stmt-input/attachment-insert-input)))
    (is (nil? (check-validate `stmt-input/statement-to-actor-insert-input)))
    (is (nil? (check-validate `stmt-input/statement-to-activity-insert-input))))
  (testing "statement insertion inputs"
    (is (nil? (check-validate `stmt-input/statement-insert-inputs 10)))
    (is (nil? (check-validate `stmt-input/statements-insert-inputs 5))))
  (testing "attachment insertion inputs"
    (is (nil? (check-validate `stmt-input/attachments-insert-inputs 10))))
  (testing "document insertion inputs"
    (is (nil? (check-validate `doc-input/document-insert-input)))))

(deftest test-query-inputs
  (testing "statement query inputs"
    (is (nil? (check-validate `stmt-input/statement-query-input)))
    (is (nil? (check-validate `agent-input/agent-query-input)))
    (is (nil? (check-validate `activity-input/activity-query-input))))
  (testing "document query inputs"
    (is (nil? (check-validate `doc-input/document-input)))
    (is (nil? (check-validate `doc-input/document-ids-input)))
    (is (nil? (check-validate `doc-input/document-multi-input)))))
