(ns lrsql.user
  "Sandbox to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as p]
            [lrsql.lrs :as lrs]
            [lrsql.system :as system]
            [lrsql.hugsql.command :as command]
            [lrsql.hugsql.input :as input]
            [lrsql.hugsql.functions :as f]))

(def stmt-1
  {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart"
             "objectType" "Activity"
             "definition" {"name"        {"en-US" "Multi Part Activity"}
                           "description" {"en-US" "Multi Part Activity Description"}}}})

(def stmt-2
  {"id"     "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homepage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" "Voided"}
   "object" {"objectType" "StatementRef"
             "id"         "030e001f-b32a-4361-b701-039a3d9fceb1"}})

(def stmt-3
  (-> stmt-1
      (assoc "id" "708b3377-2fa0-4b96-9ff1-b10208b599b1")
      (assoc "actor" {"openid"     "https://example.org"
                      "name"       "Sample Agent 3"
                      "objectType" "Agent"})
      (assoc-in ["context" "instructor"] (get stmt-1 "actor"))
      (assoc-in ["object" "id"] "http://www.example.com/tincan/activities/multipart-2")
      (assoc-in ["context" "contextActivities" "other"] [(get stmt-1 "object")])))

(comment
  (input/statement->insert-inputs
   (input/prepare-statement (dissoc stmt-1 "id")))
  
  (def sys (system/system))

  (def sys' (component/start sys))

  (def ds (jdbc/get-datasource (system/db-spec)))

  (command/insert-inputs! ds (input/statements->insert-input [stmt-1]))

  (def params
    {:statementId        "030e001f-b32a-4361-b701-039a3d9fceb1"
     :agent              "{\"mbox\":\"mailto:sample.agent@example.com\"}"
     :activity           "http://www.example.com/tincan/activities/multipart"
     :verb               "http://adlnet.gov/expapi/verbs/answered"
     :related_activities false
     :limit              "1"
     :ascending?         true})
  (p/-store-statements (:lrs sys') {} [stmt-1] [])
  (p/-store-statements (:lrs sys') {} [stmt-2 stmt-3] [])
  (p/-get-statements (:lrs sys') {} {:until "2021-05-20T16:59:08Z"} {})

  (jdbc/execute! ds ["SELECT * FROM statement_to_activity"])

  (jdbc/execute! ds ["SELECT is_voided
                      FROM xapi_statement"])

  (jdbc/execute! ds ["SELECT 1 FROM agent WHERE agent_ifi = ?" "foo"])

  (jdbc/execute! ds ["SELECT payload FROM xapi_statement WHERE
                      statement_id = ?
                      AND is_voided = false"
                     "030e001f-b32a-4361-b701-039a3d9fceb1"])

  ;; Delete everything
  (doseq [cmd ["DELETE FROM xapi_statement"
               "DELETE FROM agent"
               "DELETE FROM activity"
               "DELETE FROM attachment"
               "DELETE FROM statement_to_agent"
               "DELETE FROM statement_to_activity"
               "DELETE FROM statement_to_attachment"]]
    (jdbc/execute! ds [cmd]))

  (component/stop sys))
