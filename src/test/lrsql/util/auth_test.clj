(ns lrsql.util.auth-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lrsql.test-support :refer [check-validate instrument-lrsql]]
            [lrsql.util.auth :as au]))

(instrument-lrsql)

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
              (:result (au/authorize-action
                        {:request {:request-method request-method
                                   :path-info path-info}}
                        {:scopes scopes}))))
      ;; all scope
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
      ;; Multiple scopes
      true {:request-method :get
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read :scope/statements.write}}
      true {:request-method :post
            :path-info      "xapi/statements"
            :scopes         #{:scope/statements.read :scope/statements.write}}
      true {:request-method :get
            :path-info      "xapi/activities/state"
            :scopes         #{:scope/all :scope/statements.read}}
      true {:request-method :get
            :path-info      "xapi/activities/state"
            :scopes         #{:scope/all.read :scope/statements.read}}))
  (testing "authorize-action gentest"
    (is (nil? (check-validate `au/authorize-action)))))
