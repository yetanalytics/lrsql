(ns lrsql.util.oidc-test
  (:require [clojure.test    :refer [deftest is testing]]
            [lrsql.util.oidc :refer [get-configuration
                                     parse-scope-claim
                                     token-auth-identity
                                     make-authority-fn]]))

(deftest get-configuration-test
  (testing "Slurps configuration"
    (testing "From issuer"
      (is (= "https://server.example.com"
             (-> {:oidc-issuer "dev-resources/oidc"}
                 get-configuration
                 (get "issuer")))))
    (testing "From custom config loc"
      (is (= "https://server.example.com"
             (-> {:oidc-config "dev-resources/oidc/.well-known/openid-configuration"}
                 get-configuration
                 (get "issuer")))))))

(deftest parse-scope-claim-test
  (testing "Gets valid scopes, skips others"
    (is (= #{:scope/all
             :scope/all.read
             :scope/statements.read
             :scope/statements.write}
           (into #{}
                 (parse-scope-claim
                  "openid profile all all/read statements/read statements/write"))))))

(deftest token-auth-identity-test
  (let [auth-fn (make-authority-fn nil)]
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
              auth-fn))))
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
              auth-fn))))
    (testing "nil w/o token"
      (is (nil?
           (token-auth-identity
            {:request
             {}}
            auth-fn))))))

(deftest make-authority-fn-test
  (testing "default, from resource"
    (let [a-fn (make-authority-fn nil)]
      (is
       (= {"objectType" "Group",
           "member"
           [{"account" {"homePage" "foo", "name" "bar"}}
            {"account" {"homePage" "foo", "name" "baz"}}]}
          (a-fn {:iss "foo"
                 :aud "bar"
                 :sub "baz"})))))
  (testing "from file path"
    (let [a-fn (make-authority-fn "resources/lrsql/config/oidc_authority.json.template")]
      (is
       (= {"objectType" "Group",
           "member"
           [{"account" {"homePage" "foo", "name" "bar"}}
            {"account" {"homePage" "foo", "name" "baz"}}]}
          (a-fn {:iss "foo"
                 :aud "bar"
                 :sub "baz"}))))))
