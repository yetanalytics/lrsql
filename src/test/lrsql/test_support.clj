(ns lrsql.test-support
  (:require [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test :as otest]
            [next.jdbc :as jdbc]
            [com.yetanalytics.datasim :as ds]
            [lrsql.init.config :refer [read-config]]
            [lrsql.system :as system]
            [lrsql.sqlite.record :as sr]
            [lrsql.postgres.record :as pr]
            [lrsql.util :as u]
            [clj-test-containers.core :as tc]))

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
         res  (stest/check fname opts)]
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

;; Container spec, derived from settings
(def ^:dynamic *postgres-container*
  (let [{{{:keys [db-type
                  db-port
                  db-name
                  db-user
                  db-password
                  test-db-version]}
          :database}
         :connection} (read-config :test-postgres)]
    (tc/create
     {:image-name    (format "%s:%s" db-type test-db-version)
      :exposed-ports [db-port]
      :env-vars      {"POSTGRES_DB"       db-name
                      "POSTGRES_USER"     db-user
                      "POSTGRES_PASSWORD" db-password}
      :wait-for      {:wait-strategy :port}})))

(def table-names
  ["credential_to_scope"
   "lrs_credential"
   "admin_account"
   "state_document"
   "agent_profile_document"
   "activity_profile_document"
   "statement_to_statement"
   "statement_to_activity"
   "statement_to_actor"
   "attachment"
   "activity"
   "actor"
   "xapi_statement"
   "reaction"])

;; Utility to truncate all tables after tests in PG
(defn truncate-all-postgres!
  "Truncate all tables in the shared memory db."
  [ds]
  (jdbc/with-transaction [tx ds]
    (doseq [table-name table-names]
      (jdbc/execute! tx [(format "TRUNCATE TABLE %s CASCADE" table-name)]))))

;; Utility to truncate all tables after tests in sqlite
(defn truncate-all-sqlite!
  "Truncate all tables in the shared memory db."
  []
  (let [db {:dbtype "sqlite"
            :dbname ":memory:?cache=shared"}
        ds (jdbc/get-datasource db)]
    (jdbc/with-transaction [tx ds]
      (doseq [table-name table-names]
        (jdbc/execute! tx [(format "DELETE FROM %s" table-name)])))))

;; Returns `{}` as default - need to be used in a fixture
(defn test-system
  "Create a lrsql system specifically for tests. Optional kwarg `conf-overrides`
   takes a map where the key is a vec of a key location in config map, and the
   value is an override."
  [& {:keys [_]}]
  {})

;; `:memory:` is a special db-name value that creates an in-memory SQLite DB.

(defn fresh-sqlite-fixture
  [f]
  (let [sl-cfg (-> (read-config :test-sqlite)
                   (assoc-in [:connection :database :db-name]
                             ":memory:?cache=shared"))]
    (with-redefs
      [read-config (constantly sl-cfg)
       test-system (fn [& {:keys [conf-overrides]}]
                     (system/system (sr/map->SQLiteBackend {}) :test-sqlite
                                    :conf-overrides conf-overrides))]
      (let [ret (f)]
        ;; Wrapped in a try for fixture test
        (try (truncate-all-sqlite!)
             (catch Exception _
               ))
        ret))))

;; Need to manually override db-type because next.jdbc does not support
;; `tc`-prefixed DB types.
(defn fresh-postgres-fixture
  [f]
  (let [{{{:keys [db-type
                  db-host
                  db-port
                  db-name
                  db-user
                  db-password]}
          :database}
         :connection
         :as raw-cfg} (read-config :test-postgres)
        mapped-port   (get (:mapped-ports *postgres-container*) db-port)
        pg-cfg        (assoc-in
                       raw-cfg
                       [:connection :database :db-port]
                       mapped-port)
        db            {:dbtype   db-type
                       :dbname   db-name
                       :host     db-host
                       :port     mapped-port
                       :user     db-user
                       :password db-password}
        ds            (jdbc/get-datasource db)]
    (with-redefs
      [read-config (constantly pg-cfg)
       test-system (fn [& {:keys [conf-overrides]}]
                     (system/system (pr/map->PostgresBackend {})
                                    :test-postgres
                                    :conf-overrides conf-overrides))]
      (let [ret (f)]
        (truncate-all-postgres! ds)
        ret))))

(def fresh-db-fixture fresh-sqlite-fixture)

(defn instrumentation-fixture
  "Turn on instrumentation before running tests, turn off after."
  [f]
  (instrument-lrsql)
  (try
    (f)
    (finally (unstrument-lrsql))))

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
                :as   test}
               & {:keys [depth]
                  :or   {depth 0}}]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bench Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; We can reuse bench inputs for tests

(defn bench-statements*
  [num-statements]
  (->> "dev-resources/bench/insert_input.json"
       ds/read-input
       ds/generate-seq
       (take num-statements)))

(defn bench-statements
  [num-statements]
  (->> (bench-statements* num-statements)
       (into [])))

(def bench-queries
  (-> "dev-resources/bench/query_input.json"
      slurp
      (u/parse-json :object? false)))
