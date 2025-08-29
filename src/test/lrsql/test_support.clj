(ns lrsql.test-support
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.string :as cstr]
            [orchestra.spec.test :as otest]
            [next.jdbc.connection :refer [jdbc-url]]
            [com.yetanalytics.datasim :as ds]
            [lrsql.init.config :refer [read-config]]
            [lrsql.system :as system]
            [lrsql.sqlite.record :as sr]
            [lrsql.postgres.record :as pr]
            [lrsql.maria.record :as mr]
            [lrsql.util :as u]
            [next.jdbc :as jdbc]
            [clojure.test :as ct]
))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LRS test helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  (in-ns 'clojure.test)
  (require '[clojure.main :as m])

  (defmacro try-expr [msg form]
    (let [locals (keys &env)
          ctx-map (into {} (map (fn [s] [`'~s s]) locals))]

      `(try ~(assert-expr msg form)
            (catch Throwable t#
              (do-report {:type :error, :message ~msg,
                          :expected '~form, :actual t#})
              (let [__locals__# ~ctx-map]
                (println "🔎 Entering debug eval REPL with locals:" (keys __locals__#))
                (m/repl :eval (fn [form#]
                                (eval
                                 `(let [~@(mapcat (fn [[sym# val#]] [`~sym# val#])
                                                  __locals__#)]
                                    ~form#)))))))))

                                        ;dynamic, so can probably do that rather than alter-var-root
  (defn test-var [v]
    (when-let [t (:test (meta v))]
      (binding [*testing-vars* (conj *testing-vars* v)]
        (do-report {:type :begin-test-var, :var v})
        (inc-report-counter :test)
        (try (t)
             (catch Throwable e
               (do-report {:type :error, :message "Uncaught exception, not in assertion."
                           :expected nil, :actual e})
               (m/repl)             ;no locals so we can do it like so
               ))
        (do-report {:type :end-test-var, :var v}))))

  (comment
    (println "💥 Test failure:" ~test-name)
    (println "🧵 Message:" (.getMessage t#)))

  (in-ns 'lrsql.test-support)
  
  )


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
  "Apply `clojure.test/is` to each element of `exprs`, comparing each
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
  "Create a lrsql system specifically for tests. Optional kwarg `conf-overrides`
   takes a map where the key is a vec of a key location in config map, and the
   value is an override."
  [& {:keys [_]}]
  {})

;; `:memory:` is a special db-name value that creates an in-memory SQLite DB.

(defn fresh-sqlite-fixture
  [f]
  (let [sl-cfg (-> (read-config :test-sqlite)
                   (assoc-in [:connection :database :db-name] ":memory:"))]
    (with-redefs
      [read-config (constantly sl-cfg)
       test-system (fn [& {:keys [conf-overrides]}]
                     (system/system (sr/map->SQLiteBackend {}) :test-sqlite
                                    :conf-overrides conf-overrides))]
      (f))))


(def stratom (atom :tc))
(defn fresh-maria-fixture [f]
  (let [{{{:keys [db-type
                  db-host
                  db-port
                  test-db-version]}
          :database} :connection :as raw-config}
        (read-config :test-maria)
        
        jdbc-url {:persistent (-> {:dbtype db-type
                                   :dbname "lrsql_db"
                                   :host db-host
                                   :port db-port
                                   :allowMultiQueries true}
                                  jdbc-url)
                  :tc (-> {:dbtype db-type
                           :dbname  (u/uuid->str (u/generate-uuid))
                           :allowMultiQueries true}
                          jdbc-url
                          (cstr/replace #"mariadb:"
                                        (format "tc:mariadb:%s:"
                                                "11.7.2"
                                                #_test-db-version))
                          (cstr/replace #"//"
                                        "///"))}

        maria-config (assoc-in raw-config
                               [:connection :database :db-jdbc-url]
                               (jdbc-url @stratom))]
    (with-redefs
      [read-config (constantly maria-config)
       test-system (fn [& {:keys [conf-overrides]}]
                     (let [sys (system/system (mr/map->MariaBackend {}) :test-maria
                                              :conf-overrides conf-overrides)]
                       (when (= @stratom :persistent)
                         (let [ds (jdbc/get-datasource {:jdbcUrl (clojure.string/replace (:persistent jdbc-url) "lrsql_db" "")
                                                        :user "root"
                                                        :password "pass"})]
                           (jdbc/execute! ds ["drop database if exists lrsql_db;"])
                           (jdbc/execute! ds ["create database lrsql_db;"])))
                       sys))]
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
       test-system (fn [& {:keys [conf-overrides]}]
                     (system/system (pr/map->PostgresBackend {})
                                    :test-postgres
                                    :conf-overrides conf-overrides))]
      (f))))

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
