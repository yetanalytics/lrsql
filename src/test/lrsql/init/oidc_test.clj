(ns lrsql.init.oidc-test
  (:require [clojure.test       :refer [deftest is testing are]]
            [lrsql.init.oidc    :refer [get-configuration
                                        resolve-authority-claims
                                        make-authority-fn
                                        render-client-config]]
            [lrsql.test-support :refer [instrument-lrsql]]))

(instrument-lrsql)

(deftest get-configuration-test
  (testing "Slurps configuration"
    (testing "From issuer"
      (is (= "https://server.example.com"
             (-> {:oidc-issuer "dev-resources/oidc"
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

(deftest render-client-config-test
  (testing "Renders OIDC Client config."
    (is
     (= {"authority"                "https://idp.example.com/realm",
         "post_logout_redirect_uri" "https://lrs.example.com/logout-callback",
         "automaticSilentRenew"     true,
         "extraQueryParams"         {"audience" "https://lrs.example.com"},
         "monitorSession"           false,
         "filterProtocolClaims"     false,
         "response_type"            "code",
         "scope"                    "openid profile lrs:admin",
         "redirect_uri"             "https://lrs.example.com/login-callback",
         "client_id"                "1234"}
        (render-client-config
         {:webserver {:oidc-issuer   "https://idp.example.com/realm"
                      :oidc-audience "https://lrs.example.com"}
          :lrs       {:oidc-scope-prefix "lrs:"
                      :oidc-client-id    "1234"
                      :oidc-client-template
                      "resources/lrsql/config/oidc_client.json.template"}})))))
