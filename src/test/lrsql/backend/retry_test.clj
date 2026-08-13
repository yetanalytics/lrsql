(ns lrsql.backend.retry-test
  (:require [clojure.test :refer [deftest is testing]]
            [lrsql.backend.protocol :as bp]
            [lrsql.mariadb.record :as mariadb]
            [lrsql.postgres.record :as postgres]
            [lrsql.sqlite.record :as sqlite])
  (:import [java.sql SQLException]
           [org.postgresql.util PSQLException PSQLState]
           [org.sqlite SQLiteErrorCode SQLiteException]))

(deftest postgres-transaction-retry-classification-test
  (let [backend (postgres/map->PostgresBackend {})]
    (is (bp/-txn-retry?
         backend
         (PSQLException. "serialization" PSQLState/SERIALIZATION_FAILURE)))
    (is (bp/-txn-retry?
         backend
         (PSQLException. "deadlock" PSQLState/DEADLOCK_DETECTED)))
    (is (not (bp/-txn-retry?
              backend
              (PSQLException. "unique" PSQLState/UNIQUE_VIOLATION))))))

(deftest mariadb-and-mysql-transaction-retry-classification-test
  (let [backend (mariadb/map->MariadbBackend {})]
    (testing "SQLSTATE transaction rollbacks"
      (is (bp/-txn-retry?
           backend
           (SQLException. "serialization" "40001" 0))))
    (testing "vendor lock and concurrency error codes"
      (doseq [code [1020 1205 1213]]
        (is (bp/-txn-retry?
             backend
             (SQLException. "retryable" "HY000" code)))))
    (is (not (bp/-txn-retry?
              backend
              (SQLException. "unique" "23000" 1062))))))

(deftest sqlite-transaction-retry-classification-test
  (let [backend (sqlite/map->SQLiteBackend {})]
    (doseq [code [SQLiteErrorCode/SQLITE_BUSY
                  SQLiteErrorCode/SQLITE_BUSY_SNAPSHOT
                  SQLiteErrorCode/SQLITE_LOCKED
                  SQLiteErrorCode/SQLITE_LOCKED_SHAREDCACHE]]
      (is (bp/-txn-retry?
           backend
           (SQLiteException. "retryable" code))))
    (is (not (bp/-txn-retry?
              backend
              (SQLiteException. "constraint"
                                SQLiteErrorCode/SQLITE_CONSTRAINT))))))
