(ns lrsql.test-support
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.string :as cstr]
            [orchestra.spec.test :as otest]
            [next.jdbc.connection :refer [jdbc-url]]
            [lrsql.init.config :refer [read-config]]
            [lrsql.system :as system]
            [lrsql.h2.record :as hr]
            [lrsql.sqlite.record :as sr]
            [lrsql.postgres.record :as pr]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LRS test helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- lrsql-syms
  []
  (set (filter #(->> % namespace (re-matches #"lrsql\..*"))
               (stest/instrumentable-syms))))

(defn instrument-lrsql
  "Instrument all instrumentable functions defined in lrsql."
  []
  (otest/instrument (lrsql-syms)))

(defn unstrument-lrsql
  "Unnstrument all instrumentable functions defined in lrsql."
  []
  (otest/unstrument (lrsql-syms)))

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

(defmacro seq-is
  "Apply `clojure.test/is` to each element of `exprs`, comapring each
   result to `expected`."
  [expected & exprs]
  (let [is-exprs# (map (fn [expr] `(clojure.test/is (= ~expected ~expr)))
                       exprs)]
    `(do ~@is-exprs#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LRS test fixtures + systems
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Returns `{}` as default - need to be used in a fixture
(defn test-system
  "Create a lrsql system specifically for tests"
  []
  {})

(defn fresh-h2-fixture
  [f]
  (let [id-str (u/uuid->str (u/generate-uuid))
        h2-cfg (-> (read-config :test-h2-mem)
                   (assoc-in [:connection :database :db-name] id-str))]
    (with-redefs
     [read-config (constantly h2-cfg)
      test-system (fn []
                    (system/system (hr/map->H2Backend {}) :test-h2-mem))]
      (f))))

;; `:memory:` is a special db-name value that creates an in-memory SQLite DB.

(defn fresh-sqlite-fixture
  [f]
  (let [sl-cfg (-> (read-config :test-sqlite)
                   (assoc-in [:connection :database :db-name] ":memory:"))]
    (with-redefs
     [read-config (constantly sl-cfg)
      test-system (fn []
                    (system/system (sr/map->SQLiteBackend {}) :test-sqlite))]
      (f))))

;; Need to manually override db-type because next.jdbc does not support
;; `tc`-prefixed DB types.

(defn fresh-postgres-fixture
  [f]
  (let [id-str (u/uuid->str (u/generate-uuid))
        pg-cfg (let [{{{:keys [db-type db-host db-port
                               test-db-version]}
                       :database} :connection :as raw-cfg}
                     (read-config :test-postgres)]
                 (assoc-in
                  raw-cfg
                  [:connection :database :db-jdbc-url]
                  (-> {:dbtype db-type
                       :dbname id-str
                       :host   db-host
                       :port   db-port}
                      jdbc-url
                      (cstr/replace #"postgresql:"
                                    (format "tc:postgresql:%s:"
                                            test-db-version)))))]
    (with-redefs
     [read-config (constantly pg-cfg)
      test-system (fn []
                    (system/system (pr/map->PostgresBackend {})
                                   :test-postgres))]
      (f))))

(def fresh-db-fixture fresh-h2-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Conformance test helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Currently these are unused, but we keep them in case we need to debug later

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
