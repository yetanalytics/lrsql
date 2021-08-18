(ns lrsql.util.auth-test
  (:require [clojure.test :refer [deftest testing is are]]
            [lrsql.util.auth :as au]))

(deftest key-pair-test
  (testing "header->key-pair test"
    ;; Base64 derived using: https://www.base64encode.org/
    (is (= {:api-key "username" :secret-key "password"}
           (au/header->key-pair "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")))
    (is (nil? (au/header->key-pair "Foo dXNlcm5hbWU6cGFzc3dvcmQ=")))))

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
                                 {:scopes #{:scope/statements.write}}))))
