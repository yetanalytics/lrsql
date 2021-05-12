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

  (component/start sys)

  (def ds (jdbc/get-datasource (system/db-spec)))

  (jdbc/execute-one! ds
                     (f/insert-activity-sqlvec
                      (-> (input/statements->insert-input [stmt])
                          rest
                          rest
                          first)))
  
  (command/insert-inputs! ds (input/statements->insert-input [stmt]))

  (jdbc/execute! ds ["SELECT * FROM statement_to_activity"])

  (f/create-statement-table-sqlvec)
  (def lrs (lrs/->LearningRecordStore))
  (p/-store-statements lrs {} [stmt] [])


  (lrs/->LearningRecordStore)


  (component/stop sys))
