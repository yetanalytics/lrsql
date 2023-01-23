(ns lrsql.util.oidc-test
  (:require [clojure.test    :refer [deftest is testing are]]
            [lrsql.util.oidc :as oidc :refer [parse-scope-claim
                                              token-auth-identity
                                              token-auth-admin-identity
                                              authorize-admin-action?]]
            [lrsql.init.oidc :refer [make-authority-fn]]
            [lrsql.test-support :refer [check-validate instrument-lrsql]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(instrument-lrsql)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-scope-claim-test
  (testing "Gets valid scopes, skips others"
    (is (= #{:scope/all
             :scope/all.read
             :scope/statements.read
             :scope/statements.write}
           (into #{}
                 (parse-scope-claim
                  "openid profile all all/read statements/read statements/write")))))
  (testing "Configurable scope-prefix"
    (is (= #{:scope/all
             :scope/all.read
             :scope/statements.read
             :scope/statements.write}
           (into #{}
                 (parse-scope-claim
                  "openid profile lrsql:all lrsql:all/read lrsql:statements/read lrsql:statements/write"
                  :scope-prefix "lrsql:"))))))

(deftest token-auth-identity-test
  (let [auth-fn (make-authority-fn nil)
        scope-prefix ""]
    (testing "Returns an LRS identity"
      (is (= {:result
              {:scopes #{:scope/all}
               :prefix ""
               :auth   {:token "foo"}
               :agent  {"objectType" "Group"
                        "member"     [{"account"
                                       {"homePage" "http://example.com/realm"
                                        "name"     "someapp"}}
                                      {"account"
                                       {"homePage" "http://example.com/realm"
                                        "name"     "1234"}}]}}}
             (token-auth-identity
              {:com.yetanalytics.pedestal-oidc/token "foo"
               :request
               {:com.yetanalytics.pedestal-oidc/claims
                {:scope "openid all"
                 :iss   "http://example.com/realm"
                 :aud   "someapp"
                 :sub   "1234"}}}
              auth-fn
              scope-prefix))))
    (testing "Fails without any valid scopes"
      (is (= {:result
              :com.yetanalytics.lrs.auth/unauthorized}
             (token-auth-identity
              {:com.yetanalytics.pedestal-oidc/token "foo"
               :request
               {:com.yetanalytics.pedestal-oidc/claims
                {:scope "openid"
                 :iss   "http://example.com/realm"
                 :aud   "someapp"
                 :sub   "1234"}}}
              auth-fn
              scope-prefix))))
    (testing "nil w/o token"
      (is (nil?
           (token-auth-identity
            {:request
             {}}
            auth-fn
            scope-prefix))))))

(deftest token-auth-admin-identity-test
  (let [scope-prefix ""]
    (testing "Returns an admin identity"
      (is (= {:scopes #{:scope/admin}
              :username "1234"
              :oidc-issuer "http://example.com/realm"}
             (token-auth-admin-identity
              {:com.yetanalytics.pedestal-oidc/token "foo"
               :request
               {:com.yetanalytics.pedestal-oidc/claims
                {:scope "openid admin"
                 :iss   "http://example.com/realm"
                 :aud   "someapp"
                 :sub   "1234"}}}
              scope-prefix))))
    (testing "Fails without any valid scopes"
      (is (= ::oidc/unauthorized
             (token-auth-admin-identity
              {:com.yetanalytics.pedestal-oidc/token "foo"
               :request
               {:com.yetanalytics.pedestal-oidc/claims
                {:scope "openid"
                 :iss   "http://example.com/realm"
                 :aud   "someapp"
                 :sub   "1234"}}}
              scope-prefix))))
    (testing "nil w/o token"
      (is (nil?
           (token-auth-admin-identity
            {:request
             {}}
            scope-prefix))))))

(deftest authorize-admin-action-test
  (testing "authorize-admin-action function"
    (are [expected input]
         (= expected
            (let [{:keys [request-method path-info scopes]} input]
              (authorize-admin-action?
               {:request {:request-method request-method
                          :path-info path-info}}
               {:scopes scopes})))
      ;; Admin Scope
      ;; Currently one for all admin requests
      true {:request-method :get
            :path-info      "/admin/account"
            :scopes         #{:scope/admin}}
      false {:request-method :get
             :path-info      "/admin/account"
             :scopes         #{}}))
  (testing "authorize-admin-action gentest"
    (is (nil? (check-validate `authorize-admin-action?)))))
