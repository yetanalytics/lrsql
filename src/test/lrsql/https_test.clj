(ns lrsql.https-test
  (:require [clojure.test :refer [deftest testing is]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.system :as system]))

(support/instrument-lrsql)

(deftest https-test
  (testing "HTTPS connection"
    (let [_    (support/assert-in-mem-db)
          sys  (system/h2-system)
          sys' (component/start sys)]
      ;; We need to pass the `--insecure` arg because curl would otherwise
      ;; not accept our generate selfie certs
      (is (= 200
             (:status (curl/get "https://0.0.0.0:8443/health"
                                {:raw-args ["--insecure"]}))))
      (is (some?
           (:body (curl/get "https://0.0.0.0:8443/xapi/about"
                            {:raw-args ["--insecure"]}))))
      (testing "is not over the HTTP port"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"curl: \(35\).*wrong version number"
             (curl/get "https://0.0.0.0:8080/health"
                       {:raw-args ["--insecure"]}))))
      (component/stop sys'))))
