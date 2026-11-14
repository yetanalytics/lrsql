(ns lrsql.util.statement-conflict-test
  (:require [clojure.test :refer [deftest testing is]]
            [lrsql.backend.protocol :as bp]
            [lrsql.util.statement-conflict :as sc])
  (:import [java.sql SQLException]))

(def ^:private no-retry-backend
  (reify bp/BackendUtil
    (-txn-retry? [_ _] false)))

(deftest statement-id-violation-test
  (testing "duplicate statement IDs are recognized"
    (doseq [ex [;; PostgreSQL
                (SQLException. "ERROR: duplicate key value violates unique constraint \"xapi_statement_statement_id_key\""
                               "23505")
                ;; SQLite
                (SQLException. "[SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (UNIQUE constraint failed: xapi_statement.statement_id)"
                               nil
                               19)
                ;; MariaDB
                (SQLException. "Duplicate entry '00000000-0000-4000-8000-000000000000' for key 'statement_id'"
                               "23000"
                               1062)
                ;; MySQL
                (SQLException. "Duplicate entry '00000000-0000-4000-8000-000000000000' for key 'xapi_statement.statement_id'"
                               "23000"
                               1062)]]
      (is (sc/statement-id-violation? ex))
      (testing "as a cause"
        (is (sc/statement-id-violation? (ex-info "wrapped" {} ex))))
      (is (sc/statement-txn-retry? no-retry-backend ex))))
  (testing "other errors are not"
    (doseq [ex [(SQLException. "ERROR: duplicate key value violates unique constraint \"actor_idx\""
                               "23505")
                (SQLException. "Duplicate entry 'x' for key 'actor_idx'"
                               "23000"
                               1062)
                (SQLException. "[SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (UNIQUE constraint failed: activity.activity_iri)"
                               nil
                               19)
                (ex-info "not SQL" {})]]
      (is (not (sc/statement-id-violation? ex)))
      (is (not (sc/statement-txn-retry? no-retry-backend ex))))))
