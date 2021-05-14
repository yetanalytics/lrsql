(ns lrsql.user
  "Namespace to run the DB during development."
  (:require [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [com.yetanalytics.lrs.protocol :as p]
            [lrsql.lrs :as lrs]
            [lrsql.system :as system]
            [lrsql.hugsql.command :as command]
            [lrsql.hugsql.input :as input]
            [lrsql.hugsql.functions :as f]))

(def stmt
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

(comment
  (def sys (system/system))

  (def sys' (component/start sys))

  (def ds (jdbc/get-datasource (system/db-spec)))

  (command/insert-inputs! ds (input/statements->insert-input [stmt]))

  (def params
    {:statementId        "030e001f-b32a-4361-b701-039a3d9fceb1"
     :agent              "{\"mbox\":\"mailto:sample.agent@example.com\"}"
     :activity           "http://www.example.com/tincan/activities/multipart"
     :verb               "http://adlnet.gov/expapi/verbs/answered"
     :related_activities false
     :limit              "1"
     :ascending?         true})
  (p/-store-statements (:lrs sys') {} [stmt] [])
  (p/-get-statements (:lrs sys') {} params {})

  (jdbc/execute! ds ["SELECT COUNT(*) FROM xapi_statement"])

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
