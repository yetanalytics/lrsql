(ns lrsql.https-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]))

;; Init

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;; Tests

(deftest https-test
  (testing "HTTPS connection"
    (let [sys  (support/test-system)
          sys' (component/start sys)
          pre  (-> sys' :webserver :config :url-prefix)]
      ;; We need to pass the `--insecure` arg because curl would otherwise
      ;; not accept our generate selfie certs
      (is (= 200
             (:status (curl/get "https://0.0.0.0:8443/health"
                                {:raw-args ["--insecure"]}))))
      (is (some?
           (:body (curl/get (format "https://0.0.0.0:8443%s/about" pre)
                            {:raw-args ["--insecure"]}))))
      (testing "is not over the HTTP port"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"curl: \(35\).*wrong version number"
             (curl/get "https://0.0.0.0:8080/health"
                       {:raw-args ["--insecure"]}))))
      (component/stop sys'))))
