(ns lrsql.scan-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.init.clamav :as clam]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(support/instrument-lrsql)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def attachment-post-params
  {:basic-auth ["username" "password"]
   :headers
   {"X-Experience-API-Version" "1.0.3"
    "Content-Type"
    "multipart/mixed; boundary=105423a5219f5a63362a375ba7a64a8f234da19c7d01e56800c3c64b26bb2fa0"}
   :body       (slurp "dev-resources/clamav/attachment_body.txt")
   :throw      false})

(def doc-post-params
  {:basic-auth ["username" "password"]
   :headers    {"X-Experience-API-Version" "1.0.3"}
   :query-params
   {"activityId" "http://www.example.com/activityId/hashset"
    "agent"
    "{\"objectType\":\"Agent\",\"account\":{\"homePage\":\"http://www.example.com/agentId/1\",\"name\":\"Rick James\"}}"
    "stateId"    "f8128f68-74e2-4951-8c5f-ef7cce73b4ff"}
   :body       "I'm a little teapot"
   :throw      false})

(deftest scan-test
  (testing "File/Document Scanning"
    ;; Stub out a scanner that always fails
    (testing "Failure"
      (with-redefs [clam/init-file-scanner (fn [_]
                                             (fn [in]
                                               (slurp in)
                                               {:message "Scan Fail!"}))]
        (let [sys  (support/test-system
                    :conf-overrides {[:webserver :enable-clamav] true})
              sys' (component/start sys)
              pre  (-> sys' :webserver :config :url-prefix)]
          (try
            (testing "Attachment"
              (is (= {:status 400
                      :body   "{\"error\":{\"message\":\"Attachment scan failed, Errors: Scan Fail!\"}}"}
                     (select-keys
                      (curl/post
                       (format "http://localhost:8080%s/statements" pre)
                       attachment-post-params)
                      [:body :status]))))
            (testing "Document"
              (is (= {:status 400
                      :body   "{\"error\":{\"message\":\"Document scan failed, Error: Scan Fail!\"}}"}
                     (select-keys
                      (curl/post
                       (format "http://localhost:8080%s/activities/state" pre)
                       doc-post-params)
                      [:body :status]))))
            (finally (component/stop sys'))))))
    ;; And one that always succeeds
    (testing "Success"
      (with-redefs [clam/init-file-scanner (fn [_]
                                             (fn [in]
                                               (slurp in)
                                               nil))]
        (let [sys  (support/test-system
                    :conf-overrides {[:webserver :enable-clamav] true})
              sys' (component/start sys)
              pre  (-> sys' :webserver :config :url-prefix)]
          (try
            (testing "Attachment"
              (is (= 200
                     (:status
                      (curl/post
                       (format "http://localhost:8080%s/statements" pre)
                       attachment-post-params)))))
            (testing "Document"
              (is (= 204
                     (:status
                      (curl/post
                       (format "http://localhost:8080%s/activities/state" pre)
                       doc-post-params)))))
            (finally (component/stop sys'))))))))
