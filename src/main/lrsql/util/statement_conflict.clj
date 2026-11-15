(ns lrsql.util.statement-conflict
  "Concurrent requests that store the same statement ID can all find the ID
   absent in `lrsql.ops.command.statement/check-stmt-conflict` and then all
   insert it. The database lets one insert through and refuses the others on
   the unique `statement_id` column. In PostgreSQL that refusal also aborts the
   transaction, so the statement cannot be read again within it.

   `statement-txn-retry?` is the retry test for the statement transaction: the
   backend's own test (serialization failures, deadlocks and the like), or
   this refusal. The re-run is a new transaction, whose conflict check then
   sees the committed statement and answers as for any other duplicate. Only
   the `statement_id` key counts: any other unique violation is not retried."
  (:require [clojure.string :as cstr]
            [lrsql.backend.protocol :as bp])
  (:import [java.sql SQLException]))

(defn- causes
  [^Throwable t]
  (take-while some? (iterate #(.getCause ^Throwable %) t)))

(defn statement-id-violation?
  "Return true if `ex`, or one of its causes, is the database refusing a
   second row with the same statement ID: PostgreSQL SQLSTATE 23505 on
   `xapi_statement_statement_id_key`, SQLite `SQLITE_CONSTRAINT` on
   `xapi_statement.statement_id`, or MariaDB and MySQL error 1062 on the
   `statement_id` key."
  [ex]
  (boolean
   (some (fn [t]
           (when (instance? SQLException t)
             (let [^SQLException e t
                   state (.getSQLState e)
                   code  (.getErrorCode e)
                   msg   (str (.getMessage e))]
               (or (and (= "23505" state)
                        (cstr/includes? msg "xapi_statement_statement_id_key"))
                   (and (cstr/includes? msg "SQLITE_CONSTRAINT")
                        (cstr/includes? msg "xapi_statement.statement_id"))
                   (and (= 1062 code)
                        (cstr/includes? msg "statement_id"))))))
         (causes ex))))

(defn statement-txn-retry?
  "Return true when a statement transaction should be retried, either because
   the backend recognizes the database error or because another transaction
   inserted the same statement ID first."
  [backend ex]
  (or (bp/-txn-retry? backend ex)
      (statement-id-violation? ex)))
