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
    (let [squuid-seq   (repeatedly 1000 util/generate-squuid)
          squuid-seq'  (->> squuid-seq
                           (map util/uuid->str)
                           sort)
          squuid-seq'' (map util/str->uuid squuid-seq')]
      (is (every? (partial re-matches xsr/UuidRegEx) squuid-seq'))
      (is (every? (fn [[u1 u2]] (clj-uuid/uuid= u1 u2))
                  (map (fn [u1 u2] [u1 u2]) squuid-seq squuid-seq''))))))
