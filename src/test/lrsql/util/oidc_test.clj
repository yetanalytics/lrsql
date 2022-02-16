(ns lrsql.util.oidc-test
  (:require [clojure.test :refer :all]
            [lrsql.util.oidc :refer :all]))

(deftest get-configuration-test
  (testing "Slurps configuration"
    (testing "From issuer"
      (is (= "https://server.example.com"
             (-> {:openid-issuer "dev-resources/oidc"}
                 get-configuration
                 (get "issuer")))))
    (testing "From custom config loc"
      (is (= "https://server.example.com"
             (-> {:openid-config "dev-resources/oidc/.well-known/openid-configuration"}
                 get-configuration
                 (get "issuer")))))))
