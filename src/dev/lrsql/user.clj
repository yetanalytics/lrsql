(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]))

(comment
  (def sys (system/system :dev))
  (def sys' (component/start sys))

  (def lrs (:lrs sys'))
  (def ds (-> sys' :lrs :connection :conn-pool))

  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 0"])
  (jdbc/execute! ds ["SET TRACE_LEVEL_FILE 2"])

  (def stmt
    {"id"     "5c9cbcb0-18c0-46de-bed1-c622c03163a1"
     "actor"  {"mbox"       "mailto:sample.foo@example.com"
               "objectType" "Agent"}
     "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
               "display" {"en-US" "answered"}}
     "object" {"id" "http://www.example.com/tincan/activities/multipart"}})
  
  (lrsp/-store-statements lrs {} [stmt] [])
  (lrsp/-get-statements lrs {} {:statementId "5c9cbcb0-18c0-46de-bed1-c622c03163a1"} #{})

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
