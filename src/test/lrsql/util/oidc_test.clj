(ns lrsql.util.oidc-test
  (:require [clojure.test    :refer [deftest is testing are]]
            [lrsql.util.oidc :refer [parse-scope-claim
                                     token-auth-identity]]
            [lrsql.init.oidc :refer [make-authority-fn]]))

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
