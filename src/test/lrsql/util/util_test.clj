(ns lrsql.util.util-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-uuid]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.util :as util]))

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

(def unicode-fixtures
  {"{\"en-GB\":\"Mary Poppins!\"}" {"en-GB" "Mary Poppins!"}
   "{\"en-US\":\"X Æ A-12\"}"      {"en-US" "X Æ A-12"}
   "{\"de-DE\":\"Eren Jäger\"}"    {"de-DE" "Eren Jäger"}
   "{\"vi-VN\":\"Kevin Nguyễn\"}"  {"vi-VN" "Kevin Nguyễn"}
   "{\"uk-UA\":\"Слава Україні\"}" {"uk-UA" "Слава Україні"}
   "{\"he-IL\":\"סודהסטרים\"}"     {"he-IL" "סודהסטרים"}
   "{\"ar-AE\":\"برج خليفة\"}"     {"ar-AE" "برج خليفة"}
   "{\"th-TH\":\"เด็กใหม่\"}"        {"th-TH" "เด็กใหม่"}
   "{\"ja-JP\":\"進撃の巨人\"}"      {"ja-JP" "進撃の巨人"}
   "{\"ko-KR\":\"방탄소년단\"}"       {"ko-KR" "방탄소년단"}
   "{\"zh-CN\":\"少女时代\"}"        {"zh-CN" "少女时代"}})

(deftest json-test
  (testing "parsing JSON"
    (is (= {"foo" "bar"}
           (util/parse-json "{\"foo\":\"bar\"}")))
    (is (= {:foo "bar"}
           (util/parse-json "{\"foo\":\"bar\"}" :keyword-keys? true)))
    (is (every? (fn [[s jsn]] (= (util/parse-json s) jsn))
                unicode-fixtures))
    (is (try (util/parse-json "{\"foo\":\"bar\"} {\"baz\":\"qux\"}")
             (catch Exception e (= ::util/extra-json-input
                                   (-> e ex-data :type)))))
    (is (try (util/parse-json "[{\"foo\":\"bar\"}, {\"baz\":\"qux\"}]")
             (catch Exception e (= ::util/not-json-object
                                   (-> e ex-data :type))))))
  (testing "writing JSON"
    (is (bytes? (util/write-json {"foo" "bar"})))
    (is (= 13 ; 13 ASCII chars in "{\"foo\":\"bar\"}"
           (count (util/write-json {"foo" "bar"}))))
    (is (every? (fn [[s jsn]] (= s (util/write-json-str jsn)))
                unicode-fixtures)))
  (testing "parsing and writing JSON"
    ;; English
    (is (= "{\"en-US\":\"foo bar\"}"
           (-> "{\"en-US\":\"foo bar\"}" util/parse-json util/write-json-str)))
    (is (= {"en-US" "foo bar"}
           (-> {"en-US" "foo bar"} util/write-json-str util/parse-json)))
    ;; Mandarin Chinese
    (is (= "{\"zh-CN\":\"你好世界\"}"
           (-> "{\"zh-CN\":\"你好世界\"}" util/parse-json util/write-json-str)))
    (is (= {"zh-CN" "你好世界"}
           (-> {"zh-CN" "你好世界"} util/write-json-str util/parse-json)))))

(def example-ts-strs
  ["2023-05-10T10:45:23Z"
   "2023-05-10T10:45:23.123Z"
   "2023-05-10T10:45:23.123+01:00"
   "2023-05-10T10:45:23.123-05:30"
   "2023-05-10T10:45:23.123456789Z"
   "2023-05-10T10:45:23.123456789-03:21"])

(def example-bad-ts-str
  "Some time on Tuesday. It was hot out I think.")

(def example-norm-ts-strs
  ["2023-05-10T10:45:23.000000000Z"
   "2023-05-10T10:45:23.123000000Z"
   "2023-05-10T09:45:23.123000000Z"
   "2023-05-10T16:15:23.123000000Z"
   "2023-05-10T10:45:23.123456789Z"
   "2023-05-10T14:06:23.123456789Z"])

(deftest timstamp-test
  (testing "parsing and rendering timestamps"
    (let [ts-insts    (mapv util/str->time example-ts-strs)
          ts-str-norm (mapv util/time->str ts-insts)]
      (is (every? inst? ts-insts))
      (is (= ts-str-norm example-norm-ts-strs))))
  (testing "throws proper error on bad string"
    (try
      (util/str->time example-bad-ts-str)
      (catch Exception e
        (is (= (ex-message e) "Cannot parse nil or invalid timestamp"))
        (is (= (ex-data e) {:data-type "timestamp",
                            :type :lrsql.util/parse-failure,
                            :data example-bad-ts-str}))))))

(deftest pad-time-str-test
  (testing "pads partial datetime string"
    (is (= "2023-02-13T16:00:00.000000000Z"
           (util/pad-time-str "2023-02-13T16"))
        (= "2023-02-13T16:43:22.684982000Z"
           (util/pad-time-str "2023-02-13T16:43:22.684982000Z")))))
