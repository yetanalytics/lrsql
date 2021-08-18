(ns lrsql.util.auth-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lrsql.test-support :refer [check-validate]]
            [lrsql.util.auth :as au]))

(deftest key-pair-test
  (testing "header->key-pair test"
    ;; Base64 derived using: https://www.base64encode.org/
    (is (= {:api-key "username" :secret-key "password"}
           (au/header->key-pair "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (= {:api-key "0123456789ABCDEF" :secret-key "fedcba9876543210"}
           (au/header->key-pair "Basic MDEyMzQ1Njc4OUFCQ0RFRjpmZWRjYmE5ODc2NTQzMjEw")))
    (is (nil? (au/header->key-pair "Foo dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "Basic  dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "Basic: dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "BasicdXNlcm5hbWU6cGFzc3dvcmQ="))))
  (testing "header->key-pair and generate-key-pair gentests"
    (is (nil? (check-validate `au/header->key-pair)))
    (is (nil? (check-validate `au/generate-key-pair)))))

(deftest authorize-test
  (testing "authorize-action function"
    (are [exp act] (= exp (:result act))
      ;; all scope
      true (au/authorize-action {:request {:request-method :get
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all}})
      true (au/authorize-action {:request {:request-method :head
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all}})
      true (au/authorize-action {:request {:request-method :put
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all}})
      true (au/authorize-action {:request {:request-method :post
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all}})
      ;; all/read scope
      true (au/authorize-action {:request {:request-method :get
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all.read}})
      true (au/authorize-action {:request {:request-method :head
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/all.read}})
      false (au/authorize-action {:request {:request-method :put
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/all.read}})
      false (au/authorize-action {:request {:request-method :post
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/all.read}})
      ;; statements/read scope
      true (au/authorize-action {:request {:request-method :get
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/statements.read}})
      true (au/authorize-action {:request {:request-method :head
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/statements.read}})
      false (au/authorize-action {:request {:request-method :put
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/statements.read}})
      false (au/authorize-action {:request {:request-method :post
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/statements.read}})
      false (au/authorize-action {:request {:request-method :get
                                            :path-info "xapi/activites/state"}}
                                 {:scopes #{:scope/statements.read}})
      ;; statements/write scope
      true (au/authorize-action {:request {:request-method :put
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/statements.write}})
      true (au/authorize-action {:request {:request-method :post
                                           :path-info "xapi/statements"}}
                                {:scopes #{:scope/statements.write}})
      false (au/authorize-action {:request {:request-method :get
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/statements.write}})
      false (au/authorize-action {:request {:request-method :head
                                            :path-info "xapi/statements"}}
                                 {:scopes #{:scope/statements.write}})
      false (au/authorize-action {:request {:request-method :put
                                            :path-info "xapi/activites/state"}}
                                 {:scopes #{:scope/statements.write}})))
  (testing "authorize-action gentest"
    (is (nil? (check-validate `au/authorize-action)))))
