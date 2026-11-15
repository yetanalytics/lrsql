(ns lrsql.util.https-setting-test
  "Tests the webserver spec for `LRSQL_ENABLE_HTTPS` (`:enable-https`)."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [lrsql.init.config :refer [read-config]]
            [lrsql.spec.config :as cs]))

(deftest https-setting-config-test
  (let [config (:webserver (read-config :test-sqlite))]
    (testing "HTTPS is enabled by default"
      (is (s/valid? ::cs/webserver (dissoc config :enable-https))))
    (testing "HTTP only is valid"
      (is (s/valid? ::cs/webserver
                    (assoc config :enable-http true :enable-https false))))
    (testing "HTTPS only is valid"
      (is (s/valid? ::cs/webserver
                    (assoc config :enable-http false :enable-https true))))
    (testing "disabling both HTTP and HTTPS is refused"
      (is (not (s/valid? ::cs/webserver
                         (assoc config :enable-http false :enable-https false)))))
    (testing "a non-boolean value is refused"
      (is (not (s/valid? ::cs/webserver
                         (assoc config :enable-https "false")))))))
