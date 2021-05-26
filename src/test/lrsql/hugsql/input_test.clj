(ns lrsql.hugsql.input-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.hugsql.input.agent     :as agent-input]
            [lrsql.hugsql.input.activity  :as activity-input]
            [lrsql.hugsql.input.statement :as stmt-input]
            [lrsql.hugsql.input.document  :as doc-input]))

(deftest test-insert-inputs
  (testing "statement object insertion inputs"
    (is (nil? (check-validate `stmt-input/actor-insert-input)))
    (is (nil? (check-validate `stmt-input/activity-insert-input)))
    (is (nil? (check-validate `stmt-input/attachment-insert-input)))
    (is (nil? (check-validate `stmt-input/group-insert-input)))
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
