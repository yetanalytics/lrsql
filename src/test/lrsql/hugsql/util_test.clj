(ns lrsql.hugsql.util-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-uuid]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.hugsql.util :as util]))

(deftest squuid-test
  (testing "squuid gentests"
    (is (nil? (check-validate `util/generate-squuid*)))
    (is (nil? (check-validate `util/generate-squuid))))
  (testing "squuid monotonicity"
    (is
     (loop [squuid-seq  (repeatedly 10000 util/generate-squuid)
            squuid-seq' (rest squuid-seq)]
       (if (empty? squuid-seq')
         true
         (let [prev-squuid (first squuid-seq)
               next-squuid (first squuid-seq')]
           (if (clj-uuid/uuid< prev-squuid next-squuid)
             (recur squuid-seq' (rest squuid-seq'))
             false))))))
  (testing "squuid monotonicity (lex sort)"
    (let [squuid-seq (->> (repeatedly 1000 util/generate-squuid)
                          (map util/uuid->str))
          squuid-seq' (sort squuid-seq)]
      (is (every? (partial re-matches xsr/UuidRegEx) squuid-seq'))
      (is (= squuid-seq squuid-seq')))))
