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
  {"actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart",
             "objectType" "Activity"
             "definition" {"name"        {"en-US" "Multi Part Activity"}
                           "description" {"en-US" "Multi Part Activity Description"}}}})

(comment
  (def sys (system/system))

  (def sys' (component/start sys))

  (def ds (jdbc/get-datasource (system/db-spec)))

  #_(jdbc/execute-one! ds
                       (f/insert-activity-sqlvec
                        (-> (input/statements->insert-input [stmt])
                            rest
                            rest
                            first)))

  (command/insert-inputs! ds (input/statements->insert-input [stmt]))

  (p/-store-statements (:lrs sys') {} [stmt] [])

  (jdbc/execute! ds ["SELECT * FROM agent"])
  
  (jdbc/execute! ds ["SELECT 1 FROM agent WHERE agent_ifi = ?" "foo"])
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
