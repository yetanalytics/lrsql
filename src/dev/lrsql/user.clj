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

(def stmt-4
  {"id"          "e8477a8d-786c-48be-a703-7c8ec7eedee5"
   "actor"       {"mbox"       "mailto:sample.agent.4@example.com"
                  "name"       "Sample Agent 4"
                  "objectType" "Agent"}
   "verb"        {"id"      "http://adlnet.gov/expapi/verbs/attended"
                  "display" {"en-US" "attended"}}
   "object"      {"id"         "http://www.example.com/meetings/occurances/34534"
                  "definition" {"extensions"  {"http://example.com/profiles/meetings/activitydefinitionextensions/room"
                                               {"name" "Kilby"
                                                "id"   "http://example.com/rooms/342"}}
                                "name"        {"en-GB" "example meeting"
                                               "en-US" "example meeting"}
                                "description" {"en-GB" "An example meeting that happened on a specific occasion with certain people present."
                                               "en-US" "An example meeting that happened on a specific occasion with certain people present."}
                                "type"        "http://adlnet.gov/expapi/activities/meeting"
                                "moreInfo"    "http://virtualmeeting.example.com/345256"}
                  "objectType" "Activity"}
   "attachments" [{"usageType"   "http://example.com/attachment-usage/test"
                   "display"     {"en-US" "A test attachment"}
                   "description" {"en-US" "A test attachment (description)"}
                   "contentType" "text/plain"
                   "length"      27
                   "sha2"        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"}]})

(def stmt-4-attach
  {:content     (.getBytes "here is a simple attachment")
   :contentType "text/plain"
   :length      27
   :sha2        "495395e777cd98da653df9615d09c0fd6bb2f8d4788394cd53c56a3bfdcd848a"})

(comment
  (input/statement->insert-inputs
   (input/prepare-statement (dissoc stmt-1 "id")))
  
  (def sys (system/system))

  (def sys' (component/start sys))


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
  (p/-store-statements (:lrs sys') {} [stmt-4] [stmt-4-attach])

  (def ds ((:conn-pool (:lrs sys'))))

  (p/-get-statements (:lrs sys') {} {:until "2021-05-20T16:59:08Z"} {})

  (p/-get-statements (:lrs sys') {} {:verb "http://adlnet.gov/expapi/verbs/attended"
                                     :attachments true} {})

  (jdbc/execute! ds ["SELECT attachment_sha, content_type, content_length, content FROM attachment
                      WHERE statement_id = ?"
                     (lrsql.hugsql.util/str->uuid "e8477a8d-786c-48be-a703-7c8ec7eedee5")])

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
