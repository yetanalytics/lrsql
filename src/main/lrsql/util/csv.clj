(ns lrsql.util.csv
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]))

(defn to-csv [data fname]
  (with-open [writer (io/writer fname)]
    (csv/write-csv writer)))


(comment
  (def test-data
    [["abc" "def"]
     ["ghi" "jkl"]])

  (def test-fname)

  (to-csv test-data test-fname))
