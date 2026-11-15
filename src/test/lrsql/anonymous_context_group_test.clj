(ns lrsql.anonymous-context-group-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(def ^:private base "http://0.0.0.0:8080/xapi/statements")

(def ^:private headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "2.0.0"})

(defn- stmt [group]
  {"actor"   {"mbox" "mailto:anon-ctx@example.org" "objectType" "Agent"}
   "verb"    {"id" "http://adlnet.gov/expapi/verbs/answered"}
   "object"  {"id" "http://www.example.com/anon-ctx"}
   "context" {"contextGroups" [{"objectType" "contextGroup" "group" group}]}})

(defn- put! [id body]
  (:status (curl/put (str base "?statementId=" id)
                     {:headers headers :basic-auth ["username" "password"]
                      :body (u/write-json-str body) :throw false})))

(defn- get-json [query]
  (let [r (curl/get (str base "?" query)
                    {:headers headers :basic-auth ["username" "password"] :throw false})]
    [(:status r) (when (= 200 (:status r)) (u/parse-json (:body r)))]))

(deftest anonymous-context-group-test
  (let [sys (component/start (support/test-system))]
    (try
      (let [anon {"objectType" "Group"
                  "member"     [{"mbox" "mailto:anon-member@example.org" "objectType" "Agent"}]}
            id   (str (random-uuid))]
        (testing "an anonymous Group in contextGroups is stored"
          (is (= 204 (put! id (stmt anon)))))
        (testing "it reads back as sent"
          (let [[st body] (get-json (str "statementId=" id))]
            (is (= 200 st))
            (is (= anon (get-in body ["context" "contextGroups" 0 "group"])))))
        (testing "its member is a related agent of the statement"
          (let [[st body] (get-json (str "related_agents=true&agent="
                                         (u/url-encode "{\"mbox\":\"mailto:anon-member@example.org\"}")))]
            (is (= 200 st))
            (is (some #(= id (get % "id")) (get body "statements"))))))
      (testing "control: an identified Group still works"
        (is (= 204 (put! (str (random-uuid))
                         (stmt {"objectType" "Group"
                                "account"    {"homePage" "http://www.example.com" "name" "g1"}
                                "member"     [{"mbox" "mailto:m2@example.org" "objectType" "Agent"}]})))))
      (testing "control: a Group with neither members nor an identifier is refused, 400"
        (is (= 400 (put! (str (random-uuid)) (stmt {"objectType" "Group"})))))
      (finally
        (component/stop sys)))))
