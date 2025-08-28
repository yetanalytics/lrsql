(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]
            [lrsql.util.actor :as a-util]
            [lrsql.maria.record :as rm]))


;; SQLite
(comment
  (require
   '[lrsql.sqlite.record :as r]
   '[lrsql.lrs-test :refer [stmt-1 stmt-2 auth-ident auth-ident-oauth]])

  (def sys (system/system (r/map->SQLiteBackend {}) :test-sqlite-mem))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))


  
  (lrsp/-store-statements lrs auth-ident [stmt-1] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])

  (println
   (jdbc/execute! ds
                  ["EXPLAIN QUERY PLAN
                    SELECT count(*)
                    FROM xapi_statement"]))

  (println
   (jdbc/execute! ds
                  ["EXPLAIN QUERY PLAN
                    SELECT stmt.payload
                    FROM xapi_statement stmt
                    WHERE stmt.verb_iri = ?
                    AND (
                    SELECT (CASE WHEN COUNT(DISTINCT stmt_actors.actor_ifi) = 2 THEN 1 ELSE 0 END)
                    FROM statement_to_actor stmt_actors
                    WHERE stmt_actors.statement_id = stmt.statement_id
                    AND stmt_actors.actor_ifi IN (?, ?)
                    AND stmt_actors.usage = 'Authority'
                    )
                    "
                   (get-in stmt-2 ["verb" "id"])
                   (first (a-util/actor->ifi-coll (:agent auth-ident-oauth)))
                   (second (a-util/actor->ifi-coll (:agent auth-ident-oauth)))]))

  (do
    (doseq [cmd [;; Drop credentials table
                 "DROP TABLE IF EXISTS credential_to_scope"
                 "DROP TABLE IF EXISTS lrs_credential"
                 "DROP TABLE IF EXISTS admin_account"
                 ;; Drop document tables
                 "DROP TABLE IF EXISTS state_document"
                 "DROP TABLE IF EXISTS agent_profile_document"
                 "DROP TABLE IF EXISTS activity_profile_document"
                 ;; Drop statement tables
                 "DROP TABLE IF EXISTS statement_to_statement"
                 "DROP TABLE IF EXISTS statement_to_activity"
                 "DROP TABLE IF EXISTS statement_to_actor"
                 "DROP TABLE IF EXISTS attachment"
                 "DROP TABLE IF EXISTS activity"
                 "DROP TABLE IF EXISTS actor"
                 "DROP TABLE IF EXISTS xapi_statement"]]
      (jdbc/execute! ds [cmd]))

    (jdbc/execute!
     ds
     ["SELECT 1
       FROM pragma_table_info('admin_account')
       WHERE name = 'passhash'
       AND \"notnull\" = 1"])
    
    (jdbc/execute!
     ds
     ["SELECT 1
       FROM pragma_foreign_key_list('statement_to_actor')
       WHERE \"table\" = 'xapi_statement'
       AND on_delete = 'CASCADE'
      "])
    
    (jdbc/execute!
     ds
     ["SELECT 1
       FROM (
         SELECT sql
         FROM sqlite_master
         WHERE type='table' AND name='credential_to_scope'
       ) AS sub_query
       WHERE sub_query.sql GLOB (
      '*(''statements/write'','
    || '*''statements/read'','
    || '*''statements/read/mine'','
    || '*''all/read'','
    || '*''all'','
    || '*''define'','
    || '*''state'','
    || '*''state/read'','
    || '*''activities_profile'','
    || '*''activities_profile/read'','
    || '*''agents_profile'','
    || '*''agents_profile/read'')*'
  )"])

    (component/stop sys')))

;; PostgreSQL
(comment
  (require
   '[lrsql.postgres.record :as rp]
   '[hugsql.core :as hug])

  (def sys (system/system (rp/map->PostgresBackend {}) :test-postgres))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  ;; Sanity check queries
  (jdbc/execute! ds
                 ["SELECT stmt.payload FROM xapi_statement stmt LIMIT 1"])

  (jdbc/execute! ds
                 ["SELECT COUNT(*) FROM xapi_statement"])

  (jdbc/execute! ds
                 ["ALTER TABLE statement_to_actor ALTER COLUMN actor_ifi TYPE text"])

  ;; Real query test
  (do
    (hug/def-sqlvec-fns "lrsql/postgres/sql/query.sql")

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (def q
      (query-statements-sqlvec
       {:actor-ifi (a-util/actor->ifi {"name" "Bob Nelson", "mbox" "mailto:bob@example.org"})
        :ascending? true
        :related-actors? true
        :limit 10})))
  (println (first q))
  (count (jdbc/execute! ds q))

  (jdbc/execute! ds ["SET enable_indexscan = ON;
                      SET enable_seqscan = ON"])
  (jdbc/execute! ds ["SET enable_hashjoin = OFF;"])

  (->> (update q 0 (fn [qstr] (str "EXPLAIN ANALYZE\n" qstr)))
       (jdbc/execute! ds)
       (map (fn [x] (get x (keyword "QUERY PLAN"))))
       (run! println))

  ;; Inserting 1.2 million API keys

  (def account-id
    (-> (adp/-get-accounts lrs) first :account-id))

  (run! (fn [idx]
          (when (zero? (mod idx 1000))
            (println (str "On iteration: " idx)))
          (adp/-create-api-keys lrs account-id ["all"])
          (adp/-create-api-keys lrs account-id ["all/read"])
          (adp/-create-api-keys lrs account-id ["statements/read"])
          (adp/-create-api-keys lrs account-id ["statements/read/mine"])
          (adp/-create-api-keys lrs account-id ["statements/write"])
          (adp/-create-api-keys lrs account-id ["define"])
          (adp/-create-api-keys lrs account-id ["state"])
          (adp/-create-api-keys lrs account-id ["state/read"])
          (adp/-create-api-keys lrs account-id ["activities_profile"])
          (adp/-create-api-keys lrs account-id ["activities_profile/read"])
          (adp/-create-api-keys lrs account-id ["agents_profile"])
          (adp/-create-api-keys lrs account-id ["agents_profile/read"]))
        (range 0 100000))
  
  ;; Note: deletes initial username + password API keys
  (jdbc/execute! ds ["DELETE FROM credential_to_scope"])
  
  ;; Query enums

  (jdbc/execute!
   ds
   [
"SELECT 1
 WHERE enum_range(NULL::scope_enum)::TEXT[]
  = ARRAY[
    'statements/write',
    'statements/read',
    'statements/read/mine',
    'all/read',
    'all',
    'state',
    'state/read',
    'define',
    'activities_profile',
    'activities_profile/read',
    'agents_profile',
    'agents_profile/read'
  ];
"])

  ;; Stop system

  (component/stop sys')
  )

;; MariaDB
(comment
  (require
   '[lrsql.maria.record :as rm]
   '[hugsql.core :as hug]
   '[lrsql.init.log]
   '[lrsql.test-support :as ts]
   '[lrsql.lrs-test :refer [stmt-0 stmt-1 stmt-2 auth-ident auth-ident-oauth]]
   '[lrsql.ops.command.statement :as stmt-cmd]
   '[lrsql.backend.protocol :aps bp]
   '[clojure.data.json :as json]
   '[lrsql.maria.data :as md])
  

  (do (require '[clojure.tools.namespace.repl :refer [refresh]])
      (refresh)
      (require '[clojure.tools.namespace.repl :refer [refresh]])
      (do (component/stop sys')
          (def sys (system/system (rm/map->MariaBackend {}) :test-maria))
          (def sys' (component/start sys))
          (def lrs (:lrs sys'))
          (def bk (:backend lrs))
          (def ds (-> sys' :lrs :connection :conn-pool))))
  
  #_(lrsql.init.log/set-log-level! "DEBUG")
  (def sys (system/system (rm/map->MariaBackend {}) :test-maria))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def bk (:backend lrs))
    
  (def ds (-> sys' :lrs :connection :conn-pool))
  @stmt-cmd/holder


  
  (lrsp/-store-statements lrs auth-ident [stmt-0 stmt-1 stmt-2] [])
  (lrsp/-store-statements lrs auth-ident [stmt-2] [])
  (lrsp/-store-statements lrs auth-ident-oauth [stmt-2] [])



  
  
  (do (require '[lrsql.test-runner :as tr])
      (tr/-main {"--database" "maria"}))
  
  ;; Sanity check queries
  (jdbc/execute! ds
                 ["SELECT stmt.payload FROM xapi_statement stmt LIMIT 1"])

  (jdbc/execute! ds
                 ["SELECT COUNT(*) FROM xapi_statement"])



  ;; Stop system

  (component/stop sys')
  )


(with-open [writer (java.io.StringWriter.)]
              (adp/-get-statements-csv lrs writer  [["id"] ["actor" "mbox"] ["verb" "id"] ["object" "id"]] {})
              (str writer)
)


(require '[next.jdbc.result-set :as jrs])

(require '[lrsql.ops.query.statement :as qs])
(require '[com.yetanalytics.pathetic :as pa])

(println  (lrsp/-get-statements lrs auth-ident {} []))

(def res  (lrsp/-get-statements lrs auth-ident {} []))



(stmt-input/query-statement-input {:ascending true} auth-ident )

 (get-ss lrs auth-ident {:limit 2 :ascending true} #{})


;;;;single test harness
(comment
  (require
   '[lrsql.test-support :as ts]
   '[clojure.test :as test])

  (def implementation
    #_ts/fresh-sqlite-fixture
    #_ts/fresh-postgres-fixture
    ts/fresh-maria-fixture)

(defn test-test [test]
  (implementation #(test)))

(defn test-ns [ns]
  (do
    (require ns)
    (alter-meta! (find-ns ns)
                 assoc
                 ::test/each-fixtures
                 [implementation])
    (test/run-tests ns)))

(defmacro with-fixtures [& forms]
  `(implementation
    (fn []
      (do ~@forms)))))

(comment
;using cognitect's test runner
  (require '[cognitect.test-runner.api :as runner]
           '[lrsql.test-support :as support])
  
  (with-redefs [support/fresh-db-fixture support/fresh-maria-fixture]
    (runner/test {
                  #_#_:patterns [#"lrsql.lrs-test"]
                  :nses ['lrsql.lrs-test]
                  :dirs ["src/test"]})))




(require '[lrsql.util.statement :as us])




(+ 2 2)
(defn stdin-works? []
  (try
    ;; Try to read a line without blocking
    (when (.ready *in*)
      (.readLine (java.io.BufferedReader. *in*)))
    true
    (catch Exception _
      false)))

(defn safe-repl []
  (if (stdin-works?)
    (try
      (println "🛠️  Starting fallback REPL. Ctrl-D to exit.")
      (clojure.main/repl)
      (catch Throwable e
        (println "⚠️  Could not start fallback REPL:" (.getMessage e))))
    (println "⚠️  Input stream is not available (probably CIDER). Skipping REPL.")))

(safe-repl)







(defmacro test-macro [& body]
  `(do
     (println "test-macro!")
     ~@body))

(alter-var-root #'test-macro
                (fn [_]
                  (fn [& form]
                    `(do
                       (println "rebound!")
                       ~@(rest form)))))

(alter-meta! #'test-macro assoc :macro true)

(def v)

(alter-meta! #'v assoc :macro true)

(alter-var-root #'v (fn [_]
                      (fn [_ & form]
                        `(do
                           (println "vvvvv!!")
                           ~@form))))

(def holder (atom nil))
(defmacro env-macro [& form]
  (reset! holder  &env)
  `(do ~@form)
  )


  (require '[cognitect.test-runner.api :as runner]
           '[lrsql.test-support :as support])
(in-ns 'cognitect.test-runner)

(def unfiltered (->>  ["src/test"] 
                     (map io/file)
                     (mapcat find/find-namespaces-in-dir)
                     ))

(filter (ns-filter {:namespace [#{'lrsql.lrs-test}] :namespace-regex [#"lrs-test"]}) unfiltered)

((ns-filter {:namespace #{'lrsql.lrs-test} :namespace-regex [#"lrs-test"]}) 'lrsql.lrs-test)



(in-ns 'cognitect.test-runner)

(defn- ns-filter
  [{:keys [namespace namespace-regex] :as opts}]
  (println "opts:" opts)
  (let [regexes (or namespace-regex [#".*\-test$"])]
    (fn [ns]
      (or
       (get namespace ns)
       (some #(re-matches % (name ns)) regexes)))))

(require 'clojure.repl)
(clojure.repl/source test)

(defn test
  [options]
  (let [dirs (or (:dir options)
                 #{"test"})
        nses (->> dirs
                  (map io/file)
                  (mapcat find/find-namespaces-in-dir))
        nses (filter (ns-filter options) nses)]
    (println "nses:" nses)
    (println (format "\nRunning tests in %s" dirs))
    #_#_(dorun (map require nses))
    (try
      (filter-vars! nses (var-filter options))
      (filter-fixtures! nses)
      (apply test/run-tests nses)
      (finally
        (restore-vars! nses)
        (restore-fixtures! nses)))))

(in-ns 'lrsql.test-runner)

(with-redefs [support/fresh-db-fixture support/fresh-maria-fixture]
  (runner/test {:dirs ["src/test"]
                :nses ['lrsql.lrs-test]
                :patterns [#"nonsensestring"]
                }))

(def ds)


(defmacro capture-args []
                    (let [locals (keys &env)
                          syms (mapv (comp symbol name) locals)
                          vals (vec locals)]
                      `(let [syms# '~syms
                             vals# (vector ~@locals)]
                      (def holder (atom (zipmap syms# vals#))))))

(require '[clojure.walk :as walk])

(defmacro with-holder [form]
  (let [params (mapv (comp symbol gensym name) (keys @holder))
        mapping (zipmap (keys @holder) params)
        rewritten (walk/postwalk-replace mapping form)
        fn-form (list 'fn params rewritten)]
    `(apply (eval ~fn-form) (vals @holder))))


(defmacro do-print [& forms]
  `(do
     ~@(apply concat
              (for [form forms]
                [`(println ~(str form))
                 form]))
     (println "all done!")))
