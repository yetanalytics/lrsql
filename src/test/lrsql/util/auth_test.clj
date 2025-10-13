(ns lrsql.util.auth-test
  (:require [clojure.test :refer [deftest testing is are use-fixtures]]
            [lrsql.test-support :as support :refer [check-validate]]
            [lrsql.util.auth :as au]))

(use-fixtures :once support/instrumentation-fixture)

(deftest key-pair-test
  (testing "header->key-pair test"
    ;; Base64 derived using: https://www.base64encode.org/
    (is (= {:api-key "username" :secret-key "password"}
           (au/header->key-pair "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (= {:api-key "username" :secret-key "password"}
           (au/header->key-pair "Basic             dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (= {:api-key "0123456789ABCDEF" :secret-key "fedcba9876543210"}
           (au/header->key-pair "Basic MDEyMzQ1Njc4OUFCQ0RFRjpmZWRjYmE5ODc2NTQzMjEw")))
    (is (nil? (au/header->key-pair "Foo dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "Basic: dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "BasicdXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair nil))))
  (testing "header->key-pair and generate-key-pair gentests"
    (is (nil? (check-validate `au/header->key-pair)))
    (is (nil? (check-validate `au/generate-key-pair)))))

(deftest authorize-test
  (testing "authorize-action function"
    (are [expected input]
         (= expected
            (let [{:keys [request-method path-info scopes]} input]
              (au/authorized-action?
               {:request {:request-method request-method
                          :path-info path-info}}
               {:scopes scopes})))
      ;; all scopes
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/all}}
      true {:request-method :head
            :path-info      "xapi/statements"
            :scopes         #{:scope/all}}
      true {:request-method :put
            :path-info      "xapi/statements"
            :scopes         #{:scope/all}}
      true {:request-method :post
            :path-info      "xapi/statements"
            :scopes         #{:scope/all}}
      true {:request-method :delete
            :path-info      "xapi/activities/state"
            :scopes         #{:scope/all}}
      ;; all/read scope
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/all.read}}
      true {:request-method :head
            :path-info      "xapi/statements"
            :scopes         #{:scope/all.read}}
      false {:request-method :put
             :path-info      "xapi/statements"
             :scopes         #{:scope/all.read}}
      false {:request-method :post
             :path-info      "xapi/statements"
             :scopes         #{:scope/all.read}}
      false {:request-method :delete
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/all.read}}
      true  {:request-method :get
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/all.read}}
      ;; state scope
      true  {:request-method :get
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/state}}
      true  {:request-method :head
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/state}}
      true  {:request-method :post
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/state}}
      true  {:request-method :put
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/state}}
      true  {:request-method :delete
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/state}}
      false {:request-method :get
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/state}}
      ;; activities_profile scope
      true  {:request-method :get
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/activities_profile}}
      true  {:request-method :head
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/activities_profile}}
      true  {:request-method :post
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/activities_profile}}
      true  {:request-method :put
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/activities_profile}}
      true  {:request-method :delete
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/activities_profile}}
      false {:request-method :get
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/activities_profile}}
      false {:request-method :get
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/activities_profile}}
      ;; agents_profile scope
      true  {:request-method :get
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/agents_profile}}
      true  {:request-method :head
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/agents_profile}}
      true  {:request-method :post
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/agents_profile}}
      true  {:request-method :put
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/agents_profile}}
      true  {:request-method :delete
             :path-info      "xapi/agents/profile"
             :scopes         #{:scope/agents_profile}}
      false {:request-method :get
             :path-info      "xapi/activities/profile"
             :scopes         #{:scope/agents_profile}}
      ;; statements/read scope
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read}}
      true {:request-method :head
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read}}
      false {:request-method :put
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.read}}
      false {:request-method :post
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.read}}
      false {:request-method :get
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/statements.read}}
      ;; statements/read/mine scope
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read.mine}}
      true {:request-method :head
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read.mine}}
      false {:request-method :put
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.read.mine}}
      false {:request-method :post
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.read.mine}}
      false {:request-method :get
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/statements.read.mine}}
      ;; statements/write scope
      true {:request-method :put
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.write}}
      true {:request-method :post
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.write}}
      false {:request-method :get
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.write}}
      false {:request-method :head
             :path-info      "xapi/statements"
             :scopes         #{:scope/statements.write}}
      false {:request-method :put
             :path-info      "xapi/activities/state"
             :scopes         #{:scope/statements.write}}
      ;; Multiple scopes (non-exhaustive)
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read :scope/statements.write}}
      true {:request-method :post
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read :scope/statements.write}}
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read :scope/statements.read.mine}}
      true {:request-method :get
            :path-info      "xapi/activities/state"
            :scopes         #{:scope/all :scope/statements.read}}
      true {:request-method :get
            :path-info      "xapi/activities/state"
            :scopes         #{:scope/all.read :scope/statements.read}}
      ;; No scopes
      ;; Differs from recommended behavior in xAPI spec (where default
      ;; scopes are `statements/write` and `statements/read/mine`)
      false {:request-method :get
             :path-info      "xapi/statements"
             :scopes         #{}}
      false {:request-method :head
             :path-info      "xapi/statements"
             :scopes         #{}}
      false {:request-method :put
             :path-info      "xapi/statements"
             :scopes         #{}}
      false {:request-method :post
             :path-info      "xapi/statements"
             :scopes         #{}}
      false {:request-method :delete
             :path-info      "xapi/activities/state"
             :scopes         #{}}
      ;; Non-specified endpoints (e.g. /activities)
      true  {:request-method :get
             :path-info      "/activities"
             :scopes         #{:scope/all}}
      true  {:request-method :head
             :path-info      "/activities"
             :scopes         #{:scope/all.read}}
      true  {:request-method :put
             :path-info      "/activities"
             :scopes         #{:scope/all}}
      false {:request-method :post
             :path-info      "/activities"
             :scopes         #{:scope/all.read}}))
  (testing "authorization fn gentest"
    (is (nil? (check-validate `au/authorized-action?)))))

(deftest statement-read-mine-authorization-test
  (testing "statement-read-mine-authorization? function"
    (is (true?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/statements.read.mine}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/statements.read.mine
                     :scope/statements.read}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/statements.read.mine
                     :scope/statements.read
                     :scope/all.read}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/statements.read.mine
                     :scope/statements.read
                     :scope/all.read
                     :scope/all}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/statements.read
                     :scope/all.read
                     :scope/all}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{:scope/state}})))
    (is (false?
         (au/statement-read-mine-authorization?
          {:scopes #{}}))))
  (testing "statement-read-mine-authorization? gentest"
    (is (nil? (check-validate `au/statement-read-mine-authorization?)))))
