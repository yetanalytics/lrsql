(ns lrsql.test-support
  (:require [clojure.spec.test.alpha :as stest]
            [lrsql.util :as u])
  (:import [java.util UUID]))

(defn fresh-db-fixture
  [f]
  (let [id-str (str (UUID/randomUUID))
        cfg (-> (u/read-config :test)
                (assoc-in [:database :db-name] id-str)
                (assoc-in [:connection :database :db-name] id-str)
                (assoc-in [:lrs :database :db-name] id-str))]
    (with-redefs
      [u/read-config (constantly cfg)]
      (f))))

;; TODO: Switch to io/resource for reading config file
(defn assert-in-mem-db
  []
  (let [env     (u/read-config :test)
        db-type (-> env :database :db-type)]
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

;; TODO: This function is unused - remove or use
(defn tests-seq
  "Given nested xapi conformance logs, flatten them into a seq"
  [logs]
  (mapcat
   (fn splode [{:keys [_title
                       _status
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
