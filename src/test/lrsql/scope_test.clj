(ns lrsql.scope-test
  "HTTP tests for different scopes, as listed in this section:
   https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#details-15
   "
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [babashka.curl :as curl]
            [ring.util.codec :refer [form-encode]]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def headers
  {"Content-Type"             "application/json"
   "If-None-Match"            "true" ; for activity/agent profiles
   "X-Experience-API-Version" "1.0.3"})

;; /statements

(def stmt-endpoint
  "http://localhost:8080/xapi/statements")

(def stmt-0 ; copied from lrs-test
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor"  {"mbox"       "mailto:sample.foo@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-1
  (assoc stmt-0 "id" "00000000-0000-4000-8000-000000000001"))

(def stmt-id-0
  (get stmt-0 "id"))

(def stmt-id-1
  (get stmt-1 "id"))

(def stmt-body-0
  (u/write-json-str stmt-0))

(def stmt-body-1
  (u/write-json-str stmt-1))

(def empty-stmt-body
  (u/write-json-str []))
;; /agents

(def agent-endpoint
  "http://localhost:8080/xapi/agents")

(def agent-body
  (u/write-json-str (get stmt-0 "actor")))

;; /agents/profile

(def agent-prof-endpoint
  "http://localhost:8080/xapi/agents/profile")

(def agent-prof-body
  (u/write-json-str {"foo" 1 "bar" 2}))

(def agent-prof-body-2
  (u/write-json-str {"foo" 1 "bar" 2 "new" 3}))

(def agent-prof-id
  "http://example.org/agent/profile")

;; /activities

(def activity-endpoint
  "http://localhost:8080/xapi/activities")

(def activity-id
  (get-in stmt-0 ["object" "id"]))

;; /activities/profile

(def activity-prof-endpoint
  "http://localhost:8080/xapi/activities/profile")

(def activity-prof-body
  (u/write-json-str {"baz" 1 "qux" 2}))

(def activity-prof-body-2
  (u/write-json-str {"baz" 1 "qux" 2 "new" 3}))

(def activity-prof-id
  "http://example.org/activities/profile")

;; /activities/state

(def activity-state-endpoint
  "http://localhost:8080/xapi/activities/state")

(def activity-state-body
  (u/write-json-str {"bee" 1 "buzz" 2}))

(def activity-state-body-2
  (u/write-json-str {"bee" 1 "buzz" 2 "new" 3}))

(def activity-state-reg
  "00000000-4000-8000-0000-111111111111")

(def activity-state-id
  "some-id")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- login
  []
  (-> (curl/post
       "http://localhost:8080/admin/account/login"
       {:headers headers
        :body    (u/write-json-str {"username" "username"
                                    "password" "password"})})
      :body
      u/parse-json
      (get "json-web-token")))

(defn- get-creds*
  [headers* scopes ?authority]
  (-> (curl/post "http://localhost:8080/admin/creds"
                 {:headers headers*
                  :body (u/write-json-str (cond-> {"scopes" scopes}
                                            (some? ?authority)
                                            (assoc "agent" ?authority)))})
      :body
      u/parse-json))

(defn- get-creds
  [headers* scope]
  (get-creds* headers* [scope] nil))

(defn- add-url-params
  [endpoint params]
  (let [params-vec (reduce-kv
                    (fn [acc k v]
                      (let [k-str (name k)
                            v-str (form-encode v)]
                        (conj acc (str k-str "=" v-str))))
                    []
                    params)
        params-str (cstr/join "&" params-vec)]
    (str endpoint "?" params-str)))

(defn- try-post
  [endpoint {:strs [api-key secret-key]} {:keys [body params]}]
  (try (:status
        (curl/post (add-url-params endpoint params)
                   {:headers    headers
                    :basic-auth [api-key secret-key]
                    :body       body}))
       (catch ExceptionInfo e
         (:status (ex-data e)))))

(defn- try-put
  [endpoint {:strs [api-key secret-key]} {:keys [body params]}]
  (try (:status
        (curl/put (add-url-params endpoint params)
                  {:headers    headers
                   :basic-auth [api-key secret-key]
                   :body       body}))
       (catch ExceptionInfo e
         (:status (ex-data e)))))

(defn- try-delete
  [endpoint {:strs [api-key secret-key]} {:keys [params]}]
  (try (:status
        (curl/delete (add-url-params endpoint params)
                     {:headers    headers
                      :basic-auth [api-key secret-key]}))
       (catch ExceptionInfo e
         (:status (ex-data e)))))

(defn- try-get
  [endpoint {:strs [api-key secret-key]} {:keys [params]}]
  (try (:status
        (curl/get (add-url-params endpoint params)
                  {:headers    headers
                   :basic-auth [api-key secret-key]}))
       (catch ExceptionInfo e
         (:status (ex-data e)))))

(defn- try-head
  [endpoint {:strs [api-key secret-key]} {:keys [params]}]
  (try (:status
        (curl/head (add-url-params endpoint params)
                   {:headers    headers
                    :basic-auth [api-key secret-key]}))
       (catch ExceptionInfo e
         (:status (ex-data e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each support/fresh-db-fixture)

(defmacro def-scope-test
  [scope status-map]
  (let [test-name (-> (cstr/replace scope #"\/" "-") (str "-scope-test"))
        {statement-write?      :write?
         statement-read?       :read?} (get status-map "/statements")
        {agent-read?           :read?} (get status-map "/agents")
        {agent-prof-write?     :write?
         agent-prof-read?      :read?} (get status-map "/agents/profile")
        {activity-read?        :read?} (get status-map "/activities")
        {activity-prof-write?  :write?
         activity-prof-read?   :read?} (get status-map "/activities/profile")
        {activity-state-write? :write?
         activity-state-read?  :read?} (get status-map "/activities/state")]
    `(deftest ~(symbol test-name)
       (let [~'sys      (support/test-system)
             ~'sys*     (component/start ~'sys)
             ~'jwt      (login)
             ~'headers* (merge ~headers {"Authorization" (str "Bearer " ~'jwt)})
             ~'creds    (get-creds ~'headers* ~scope)]
         (try
           (testing "/statements"
             (testing "POST"
               (is (= ~(if statement-write? 200 403)
                      (try-post ~stmt-endpoint
                                ~'creds
                                {:body ~stmt-body-0}))))
             (testing "PUT"
               (is (= ~(if statement-write? 204 403)
                      (try-put ~stmt-endpoint
                               ~'creds
                               {:body   ~stmt-body-1
                                :params {:statementId ~stmt-id-1}}))))
             (testing "GET"
               (is (= ~(cond
                         (and statement-write?
                              statement-read?) 200
                         statement-read?       404
                         :else                 403)
                      (try-get ~stmt-endpoint
                               ~'creds
                               {:params {:statementId ~stmt-id-0}}))))
             (testing "HEAD"
               (is (= ~(cond
                         (and statement-write?
                              statement-read?) 200
                         statement-read?       404
                         :else                 403)
                      (try-head ~stmt-endpoint
                                ~'creds
                                {:params {:statementId ~stmt-id-0}})))))
           (testing "/agents"
             ;; The LRS MUST return an agent even if none is found in the DB, as
             ;; per the spec, hence why there is no `statement-write?` check
             (testing "GET"
               (is (= ~(if agent-read? 200 403)
                      (~try-get ~agent-endpoint
                                ~'creds
                                {:params {:agent ~agent-body}}))))
             (testing "HEAD"
               (is (= ~(if agent-read? 200 403)
                      (~try-head ~agent-endpoint
                                 ~'creds
                                 {:params {:agent ~agent-body}})))))
           (testing "/agents/profile"
             (testing "PUT"
               (is (= ~(if agent-prof-write? 204 403)
                      (~try-put ~agent-prof-endpoint
                                ~'creds
                                {:body   ~agent-prof-body
                                 :params {:agent     ~agent-body
                                          :profileId ~agent-prof-id}}))))
             (testing "POST"
               (is (= ~(if agent-prof-write? 204 403)
                      (~try-post ~agent-prof-endpoint
                                 ~'creds
                                 {:body   ~agent-prof-body-2
                                  :params {:agent     ~agent-body
                                           :profileId ~agent-prof-id}}))))
             (testing "GET"
               (is (= ~(cond
                         (and agent-prof-write?
                              agent-prof-read?) 200
                         agent-prof-read?       404
                         :else                  403)
                      (~try-get ~agent-prof-endpoint
                                ~'creds
                                {:params {:agent     ~agent-body
                                          :profileId ~agent-prof-id}}))))
             (testing "HEAD"
               (is (= ~(cond
                         (and agent-prof-write?
                              agent-prof-read?) 200
                         agent-prof-read?       404
                         :else                  403)
                      (~try-head ~agent-prof-endpoint
                                 ~'creds
                                 {:params {:agent     ~agent-body
                                           :profileId ~agent-prof-id}}))))
             (testing "DELETE"
               (is (= ~(if agent-prof-write? 204 403)
                      (~try-delete ~agent-prof-endpoint
                                   ~'creds
                                   {:params {:agent     ~agent-body
                                             :profileId ~agent-prof-id}})))))
           (testing "/activities"
             (testing "GET"
               (is (= ~(cond
                         (and statement-write?
                              activity-read?) 200
                         activity-read?       404
                         :else                403)
                      (~try-get ~activity-endpoint
                                ~'creds
                                {:params {:activityId ~activity-id}}))))
             (testing "HEAD"
               (is (= ~(cond
                         (and statement-write?
                              activity-read?) 200
                         activity-read?       404
                         :else                403)
                      (~try-head ~activity-endpoint
                                 ~'creds
                                 {:params {:activityId ~activity-id}})))))
           (testing "/activities/profile"
             (testing "PUT"
               (is (= ~(if activity-prof-write? 204 403)
                      (~try-put ~activity-prof-endpoint
                                ~'creds
                                {:body   ~activity-prof-body
                                 :params {:activityId ~activity-id
                                          :profileId  ~activity-prof-id}}))))
             (testing "POST"
               (is (= ~(if activity-prof-write? 204 403)
                      (~try-post ~activity-prof-endpoint
                                 ~'creds
                                 {:body   ~activity-prof-body-2
                                  :params {:activityId ~activity-id
                                           :profileId  ~activity-prof-id}}))))
             (testing "GET"
               (is (= ~(cond
                         (and activity-prof-write?
                              activity-prof-read?) 200
                         activity-prof-read?       404
                         :else                     403)
                      (~try-get ~activity-prof-endpoint
                                ~'creds
                                {:params {:activityId ~activity-id
                                          :profileId  ~activity-prof-id}}))))
             (testing "HEAD"
               (is (= ~(cond
                         (and activity-prof-write?
                              activity-prof-read?) 200
                         activity-prof-read?       404
                         :else                     403)
                      (~try-head ~activity-prof-endpoint
                                 ~'creds
                                 {:params {:activityId ~activity-id
                                           :profileId  ~activity-prof-id}}))))
             (testing "DELETE"
               (is (= ~(if activity-prof-write? 204 403)
                      (~try-delete ~activity-prof-endpoint
                                   ~'creds
                                   {:params {:activityId ~activity-id
                                             :profileId  ~activity-prof-id}})))))
           (testing "/activities/state"
             (testing "PUT"
               (is (= ~(if activity-state-write? 204 403)
                      (~try-put ~activity-state-endpoint
                                ~'creds
                                {:body   ~activity-state-body
                                 :params {:activityId   ~activity-id
                                          :agent        ~agent-body
                                          :registration ~activity-state-reg
                                          :stateId      ~activity-state-id}}))))
             (testing "POST"
               (is (= ~(if activity-state-write? 204 403)
                      (~try-post ~activity-state-endpoint
                                 ~'creds
                                 {:body   ~activity-state-body-2
                                  :params {:activityId   ~activity-id
                                           :agent        ~agent-body
                                           :registration ~activity-state-reg
                                           :stateId      ~activity-state-id}}))))
             (testing "GET"
               (is (= ~(cond
                         (and activity-state-write?
                              activity-state-read?) 200
                         activity-state-read?       404
                         :else                      403)
                      (~try-get ~activity-state-endpoint
                                ~'creds
                                {:params {:activityId   ~activity-id
                                          :agent        ~agent-body
                                          :registration ~activity-state-reg
                                          :stateId      ~activity-state-id}}))))
             (testing "HEAD"
               (is (= ~(cond
                         (and activity-state-write?
                              activity-state-read?) 200
                         activity-state-read?       404
                         :else                      403)
                      (~try-head ~activity-state-endpoint
                                 ~'creds
                                 {:params {:activityId   ~activity-id
                                           :agent        ~agent-body
                                           :registration ~activity-state-reg
                                           :stateId      ~activity-state-id}}))))
             (testing "DELETE"
               (is (= ~(if activity-state-write? 204 403)
                      (~try-delete ~activity-state-endpoint
                                   ~'creds
                                   {:params {:activityId   ~activity-id
                                             :agent        ~agent-body
                                             :registration ~activity-state-reg
                                             :stateId      ~activity-state-id}})))))
           (finally
             (component/stop ~'sys*)))))))

(def-scope-test "all"
  {"/statements"         {:read?  true
                          :write? true}
   "/agents"             {:read?  true
                          :write? true}
   "/agents/profile"     {:read?  true
                          :write? true}
   "/activities"         {:read?  true
                          :write? true}
   "/activities/profile" {:read?  true
                          :write? true}
   "/activities/state"   {:read?  true
                          :write? true}})

(def-scope-test "all/read"
  {"/statements"         {:read?  true
                          :write? false}
   "/agents"             {:read?  true
                          :write? false}
   "/agents/profile"     {:read?  true
                          :write? false}
   "/activities"         {:read?  true
                          :write? false}
   "/activities/profile" {:read?  true
                          :write? false}
   "/activities/state"   {:read?  true
                          :write? false}})

(def-scope-test "statements/write"
  {"/statements" {:read?  false
                  :write? true}})

(def-scope-test "statements/read"
  {"/statements" {:read?  true
                  :write? false}})

(def-scope-test "statements/read/mine"
  {"/statements" {:read?  true
                  :write? false}})

(def-scope-test "state"
  {"/activities/state" {:read?  true
                        :write? true}})

(def-scope-test "activities_profile"
  {"/activities/profile" {:read?  true
                          :write? true}})

(def-scope-test "agents_profile"
  {"/agents/profile" {:read?  true
                      :write? true}})

(deftest statement-read-mine-authority-scope-test
  (let [sys      (support/test-system)
        sys*     (component/start sys)
        jwt      (login)
        headers* (merge headers {"Authorization" (str "Bearer " jwt)})
        creds-1  (get-creds* headers*
                             ["statements/read/mine" "statements/write"]
                             {"mbox" "mailto:agent1@yetanalytics.com"})
        creds-2  (get-creds* headers*
                             ["statements/read/mine" "statements/write"]
                             {"mbox" "mailto:agent2@yetanalytics.com"})]
    (try
      (testing "/statements POST"
        (is (= 200
               (try-post stmt-endpoint creds-1 {:body stmt-body-0})))
        (is (= 200
               (try-post stmt-endpoint creds-2 {:body stmt-body-1})))
        (is (= 200
               (try-post stmt-endpoint creds-1 {:body empty-stmt-body}))))
      (testing "/statements GET with correct authority"
        (is (= 200
               (try-get stmt-endpoint
                        creds-1
                        {:params {:statementId stmt-id-0}})))
        (is (= 200
               (try-get stmt-endpoint
                        creds-2
                        {:params {:statementId stmt-id-1}}))))
      (testing "/statements HEAD with correct authority"
        (is (= 200
               (try-head stmt-endpoint
                         creds-1
                         {:params {:statementId stmt-id-0}})))
        (is (= 200
               (try-head stmt-endpoint
                         creds-2
                         {:params {:statementId stmt-id-1}}))))
      ;; Treated as 404 Not Found as the statement does not exist within the
      ;; scope of the authority, rather than a blanket 403 Forbidden.
      (testing "/statements GET with wrong authority"
        (is (= 404
               (try-get stmt-endpoint
                        creds-1
                        {:params {:statementId stmt-id-1}})))
        (is (= 404
               (try-get stmt-endpoint
                        creds-2
                        {:params {:statementId stmt-id-0}}))))
      (testing "/statements HEAD with wrong authority"
        (is (= 404
               (try-head stmt-endpoint
                         creds-1
                         {:params {:statementId stmt-id-1}})))
        (is (= 404
               (try-head stmt-endpoint
                         creds-2
                         {:params {:statementId stmt-id-0}}))))
      (finally
        (component/stop sys*)))))
