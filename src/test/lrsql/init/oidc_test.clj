(ns lrsql.init.oidc-test
  (:require [clojure.test    :refer [deftest is testing are]]
            [lrsql.init.oidc :refer [get-configuration
                                     resolve-authority-claims
                                     make-authority-fn]]))

(deftest get-configuration-test
  (testing "Slurps configuration"
    (testing "From issuer"
      (is (= "https://server.example.com"
             (-> {:oidc-issuer "dev-resources/oidc"
                  :oidc-verify-remote-issuer false}
                 get-configuration
                 (get "issuer")))))
    (testing "From custom config loc"
      (is (= "https://server.example.com"
             (-> {:oidc-config "dev-resources/oidc/.well-known/openid-configuration"
                  :oidc-verify-remote-issuer false}
                 get-configuration
                 (get "issuer")))))))

(deftest resolve-authority-claims-test
  (testing "resolves client id"
    (are [claims resolved-id]
        (= (:lrsql/resolved-client-id
            (resolve-authority-claims
             (merge
              ;; other unrelated claims so spec is satisfied
              {:scope "openid all"
               :iss   "http://example.com/realm"
               :sub   "1234"}
              claims)))
           resolved-id)
      {:aud "foo"}         "foo"
      {:aud ["foo" "bar"]} "foo"
      {:aud       "foo"
       :client_id "bar"
       :azp       "baz"}   "bar"
      {:aud       "foo"
       :azp       "baz"}   "baz")))

(deftest make-authority-fn-test
  (testing "default, from resource"
    (let [a-fn (make-authority-fn nil)]
      (is
       (= {"objectType" "Group",
           "member"
           [{"account" {"homePage" "foo", "name" "bar"}}
            {"account" {"homePage" "foo", "name" "baz"}}]}
          (a-fn {:scope "openid all"
                 :iss   "foo"
                 :aud   "bar"
                 :sub   "baz"})))))
  (testing "from file path"
    (let [a-fn (make-authority-fn "resources/lrsql/config/oidc_authority.json.template")]
      (is
       (= {"objectType" "Group",
           "member"
           [{"account" {"homePage" "foo", "name" "bar"}}
            {"account" {"homePage" "foo", "name" "baz"}}]}
          (a-fn {:scope "openid all"
                 :iss   "foo"
                 :aud   "bar"
                 :sub   "baz"}))))))
