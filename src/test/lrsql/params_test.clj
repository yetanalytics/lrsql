(ns lrsql.params-test
  "REST API parameter tests"
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-map
  {:headers {"Content-Type"             "application/json"
             "X-Experience-API-Version" "1.0.3"}
   :basic-auth ["username" "password"]})

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; There are three non-spec query params that are defined in the upstream lrs
;; lib: `page`, `from`, and `unwrap_html`.
;; `from` is actively used and needs to be validated, `unwrap_html` is used by
;; the UI but not the backend, and `page` is completely unused.
;; See: https://github.com/yetanalytics/lrs/blob/master/src/main/com/yetanalytics/lrs/pedestal/interceptor/xapi.cljc

(deftest extra-params-test
  (testing "Extra parameters"
    (let [sys  (support/test-system)
          sys' (component/start sys)]
      (is (= 400
             (try (curl/get "http://0.0.0.0:8080/xapi/statements?foo=bar"
                            get-map)
                  (catch ExceptionInfo e (-> e ex-data :status)))))
      (testing "- `from`"
        (is (= 200
               (:status
                (curl/get "http://0.0.0.0:8080/xapi/statements?from=00000000-4000-8000-0000-111122223333"
                          get-map))))
        (is (= 400
               (try (curl/get "http://0.0.0.0:8080/xapi/statements?from=2024-10-10T10:10:10Z"
                              get-map)
                    (catch ExceptionInfo e (-> e ex-data :status))))))
      (testing "- `unwrap_html`"
        (is (= 200
               (:status
                (curl/get "http://0.0.0.0:8080/xapi/statements?unwrap_html=true"
                          get-map))))
        ;; TODO: Disallow non-boolean `unrwap_html` values?
        (is (= 200
               (:status
                (curl/get "http://0.0.0.0:8080/xapi/statements?unwrap_html=not-a-boolean"
                          get-map)))))
      (testing "- `page`"
        ;; TODO: Disallow `page` param?
        (is (= 200
               (:status
                (curl/get "http://0.0.0.0:8080/xapi/statements?page=123"
                          get-map)))))
      (component/stop sys'))))
