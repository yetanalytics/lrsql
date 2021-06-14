(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]))

(comment
  (require '[next.jdbc :as jdbc]
           '[com.yetanalytics.lrs.protocol :as lrsp]
           '[lrsql.util :as util]
           '[criterium.core :as crit])

  (def sys (system/system))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 2"])

  (count (:statements (:statement-result (lrsp/-get-statements lrs {} {:limit 50} #{}))))

  (crit/bench
   (do (lrsp/-get-statements lrs {} {} #{})
       (lrsp/-get-statements lrs {} {:registration "21a65d8b-c708-4a86-9f39-72f1c023c832"} #{})
       #_(lrsp/-get-statements lrs {} {:verb "https://w3id.org/xapi/video/verbs/seeked"} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true :since "2000-01-01T01:00:00Z"} #{})
       #_(lrsp/-get-statements lrs {} {:ascending true :until "3000-01-01T01:00:00Z"} #{})))

  "
                          WHERE 1 AND ((1 AND stmt.verb_iri = ?)
                          OR (1 AND stmt_desc.verb_iri = ?))
                          ORDER BY stmt.id ASC

   "
  
  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT stmt.id, stmt.payload
                          FROM xapi_statement stmt
                          LEFT JOIN statement_to_statement ON stmt.statement_id = statement_to_statement.ancestor_id
                          LEFT JOIN xapi_statement stmt_desc ON stmt_desc.statement_id = statement_to_statement.descendant_id
                          "
                        ;;  "http://adlnet.gov/expapi/verbs/interacted"
                        ;;  "http://adlnet.gov/expapi/verbs/interacted"
                        ;;  "http://id.tincanapi.com/verb/skipped"
                        ;;  "http://id.tincanapi.com/verb/skipped"
                        ;;  (util/str->uuid "cf6ebac3-ca56-41ae-bd1a-374fbe8a4289")
                        ;;  (util/str->uuid "cf6ebac3-ca56-41ae-bd1a-374fbe8a4289")
                        ;;  (util/time->uuid (util/str->time "2000-01-01T01:00:00Z"))
                        ;;  (util/time->uuid (util/str->time "3000-01-01T01:00:00Z"))
                         ])
      first
      :PLAN
      print)
  
  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT DISTINCT id, payload FROM
                          (SELECT stmt.id, stmt.payload FROM xapi_statement AS stmt
                           WHERE stmt.verb_iri = ?)
                          UNION
                          (SELECT DISTINCT stmt_ans.id, stmt_ans.payload FROM xapi_statement AS stmt_ans
                           INNER JOIN statement_to_statement s_t_s ON stmt_ans.statement_id = s_t_s.ancestor_id
                           INNER JOIN xapi_statement stmt_desc ON stmt_desc.statement_id = s_t_s.descendant_id
                             AND stmt_desc.verb_iri = ?)
                          "
                         "http://adlnet.gov/expapi/verbs/interacted"
                         "http://adlnet.gov/expapi/verbs/interacted"
                         ])
      first
      :PLAN
      print)
  
  (jdbc/execute! ds ["SELECT * FROM statement_to_statement"])

  (jdbc/execute! ds ["ANALYZE TABLE xapi_statement SAMPLE_SIZE 0"])

  (jdbc/execute! ds ["SELECT COLUMN_NAME, SELECTIVITY, COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                      WHERE TABLE_NAME = 'ACTOR'"])
  
  (count
   (jdbc/execute! ds ["SELECT COUNT(id) AS paycount, verb_iri
                       FROM xapi_statement
                       GROUP BY verb_iri
                       ORDER BY paycount DESC"]))

  
  (do
    (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS tbl_1 (
                      id INTEGER NOT NULL PRIMARY KEY,
                      num INTEGER NOT NULL
                      )"])

    (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS tbl_2 (
                      id INTEGER NOT NULL PRIMARY KEY,
                      ans_id INTEGER NOT NULL,
                      desc_id INTEGER NOT NULL,
                      FOREIGN KEY (ans_id) REFERENCES tbl_1(id),
                      FOREIGN KEY (desc_id) REFERENCES tbl_1(id)
                      )"])

    ;; (jdbc/execute! ds ["ALTER TABLE tbl_1 ADD FOREIGN KEY (id2) REFERENCES tbl_2(ans_id)"])
    ;; (jdbc/execute! ds ["ALTER TABLE tbl_1 ADD FOREIGN KEY (id2) REFERENCES tbl_2(desc_id)"])
    ;; (jdbc/execute! ds ["ALTER TABLE tbl_2 ADD FOREIGN KEY (ans_id) REFERENCES tbl_1(id2)"])
    ;; (jdbc/execute! ds ["ALTER TABLE tbl_2 ADD FOREIGN KEY (desc_id) REFERENCES tbl_1(id2)"])
    (jdbc/execute! ds ["CREATE INDEX ON tbl_1(num)"])

    (doseq [cmd [;; Table 1
                 "INSERT INTO tbl_1 SET id = 1, num = 6"
                 "INSERT INTO tbl_1 SET id = 2, num = 5"
                 "INSERT INTO tbl_1 SET id = 3, num = 4"
                 "INSERT INTO tbl_1 SET id = 4, num = 3"
                 "INSERT INTO tbl_1 SET id = 5, num = 2"
                 "INSERT INTO tbl_1 SET id = 6, num = 1"
               ;; Table 2
                 "INSERT INTO tbl_2 SET id = 1, ans_id = 2, desc_id = 1"
                 "INSERT INTO tbl_2 SET id = 2, ans_id = 2, desc_id = 3"
                 "INSERT INTO tbl_2 SET id = 3, ans_id = 2, desc_id = 5"
                 "INSERT INTO tbl_2 SET id = 4, ans_id = 2, desc_id = 4"]]
      (jdbc/execute! ds [cmd])))


  
  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT t_ans.id FROM tbl_1 AS t_ans
                          LEFT JOIN tbl_2 ON tbl_2.ans_id = t_ans.id
                          LEFT JOIN tbl_1 t_desc ON tbl_2.desc_id = t_desc.id
                          WHERE t_ans.num = 4 OR t_desc.num = 4"])
      first
      :PLAN
      print)
  
  (-> (jdbc/execute! ds ["EXPLAIN ANALYZE
                          SELECT id FROM
                          (SELECT t_ans.id FROM tbl_1 AS t_ans
                           WHERE t_ans.num = 4)
                          UNION
                          (SELECT t_ans.id FROM tbl_1 AS t_ans
                           INNER JOIN tbl_2 ON tbl_2.ans_id = t_ans.id
                           INNER JOIN tbl_1 t_desc ON tbl_2.desc_id = t_desc.id
                             AND t_desc.num = 4)
                          "])
      first
      :PLAN
      print)
  
  (do
    (jdbc/execute! ds ["DROP TABLE tbl_2"])
    (jdbc/execute! ds ["DROP TABLE tbl_1"]))

  (do
    (doseq [cmd [;; Drop document tables
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

    (component/stop sys')))
