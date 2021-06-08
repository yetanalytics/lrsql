(ns lrsql.test-support
  (:require [aero.core :refer [read-config]]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as cs])
  (:import [java.util UUID]))


#_(defn fresh-db-fixture
  [f]
  (with-redefs
    [env (assoc-in env [:connection :db-name] (str (UUID/randomUUID)))]
    (f)))

(defn assert-in-mem-db
  []
  (let [env     (read-config "config.edn" {:profile :test})
        db-type (-> env :connection :db-type)]
    (when (not= "h2:mem" db-type)
      (throw (ex-info "Test can only be run on in-memory H2 database!"
                      {:type    ::non-mem-db
                       :db-type db-type})))))

;; Copied from training-commons.xapi.statement-gen-test
(defn check-validate
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

(defn tests-seq
  "Given nested xapi conformance logs, flatten them into a seq"
  [logs]
  (mapcat
   (fn splode [{:keys [title
                       status
                       tests]
                :as test}
               & {:keys [depth]
                  :or {depth 0}}]
     (cons (-> test
               (assoc :depth depth))
           (mapcat #(splode % :depth (inc depth))
                   tests)))
   (map :log logs)))

(defn test-codes
  "Extract codes from a test"
  [{:keys [title name requirement]}]
  (re-seq
   #"XAPI-\d\d\d\d\d"
   (str
    title
    name
    (if (coll? requirement)
      (apply str requirement)
      requirement))))

(defn req-code-set
  "Return a set of mentioned XAPI-XXXXX codes in test results"
  [tests]
  (into #{}
        (mapcat
         test-codes
         tests)))

(defn filter-code
  "Filter tests with the given code"
  [code tests]
  (filter
   #(contains?
     (into #{}
           (test-codes
            %))
     code)
   tests))
