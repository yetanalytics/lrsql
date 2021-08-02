(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.admin.protocol :as adp]
            [lrsql.util :as u]))

(comment
  (require
   '[lrsql.postgres.record :as ir]
   '[lrsql.lrs-test :refer :all])

  (def sys (system/system (ir/map->PostgresBackend {}) :test-postgres))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (let [stmts (-> (lrsp/-get-statements lrs {} {} {})
                  :statement-result
                  :statements)
        stmt-refs
        (mapv (fn [stmt]
                {"id"     (u/uuid->str (java.util.UUID/randomUUID))
                 "actor"  {"mbox" "mailto:foo@example.org"}
                 "verb"   {"id" "http://example.org/reference"}
                 "object" {"objectType" "StatementRef"
                           "id"         (get stmt "id")}})
              stmts)]
    (lrsp/-store-statements lrs {} stmt-refs []))

  (dotimes [_ 1000]
    (lrsp/-get-statements lrs {} {:verb "https://w3id.org/xapi/video/verbs/seeked"} #{})
    (lrsp/-get-statements lrs {} {:agent {"mbox" "mailto:steve@example.org"}} #{})
    (lrsp/-get-statements lrs {} {:activity "https://books.allogy.com/v1/tenant/8/media/cc489c25-8215-4e2d-977d-8dbee098b521"} #{})
    (lrsp/-get-statements lrs {} {:agent {"mbox" "mailto:steve@example.org"}
                                  :activity "https://books.allogy.com/v1/tenant/8/media/cc489c25-8215-4e2d-977d-8dbee098b521"} #{}))

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

    (component/stop sys'))
  )
