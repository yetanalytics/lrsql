(ns lrsql.https-disabled-test
  "Tests for `LRSQL_ENABLE_HTTPS` (`:enable-https`)."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(deftest https-disabled-test
  (testing "with HTTPS disabled"
    (let [sys  (support/test-system
                :conf-overrides {[:webserver :enable-https] false})
          sys' (component/start sys)]
      (try
        (testing "HTTP is still served"
          (is (= 200 (:status (curl/get "http://0.0.0.0:8080/health")))))
        (testing "nothing listens on the SSL port"
          ;; curl exit code 7: failed to connect
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"curl: \(7\)"
               (curl/get "https://0.0.0.0:8443/health"
                         {:raw-args ["--insecure"]}))))
        (testing "no SSL connector is configured"
          (let [opts (get-in sys' [:webserver :service
                                   :io.pedestal.http/container-options])]
            (is (false? (:ssl? opts)))
            (is (nil? (:ssl-port opts)))
            (is (false? (:h2? opts)))))
        (finally (component/stop sys'))))))
