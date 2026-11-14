(ns lrsql.security-headers-test
  "Tests that the `LRSQL_SEC_HEAD_*` headers reach every response, not only
   the admin API and OpenAPI routes."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

(def ^:private frame-marker
  "SAMEORIGIN")

(def ^:private csp-marker
  "default-src 'self'; frame-ancestors 'none'")

(def ^:private header-conf
  {[:webserver :sec-head-frame]        frame-marker
   [:webserver :sec-head-content-type] "[default]"
   [:webserver :sec-head-content]      csp-marker})

(def ^:private expected-headers
  {"x-frame-options"         frame-marker
   "x-content-type-options"  "nosniff"
   "content-security-policy" csp-marker})

(defn- get-headers
  "GET `path` on the HTTP port without throwing on error statuses. Return the
   final status and the security headers."
  [path]
  (let [{:keys [status headers]}
        (curl/get (str "http://0.0.0.0:8080" path)
                  {:throw false})]
    {:status  status
     :headers (select-keys headers (keys expected-headers))}))

(deftest headers-on-every-response-test
  (let [sys  (support/test-system :conf-overrides header-conf)
        sys' (component/start sys)]
    (try
      ;; A nil status is not checked: `/` redirects to the admin UI page,
      ;; whose status depends on whether the admin UI bundle is installed.
      (doseq [[desc path status] [["health check"             "/health"          200]
                                  ["xAPI about"               "/xapi/about"      200]
                                  ["refused xAPI request"     "/xapi/statements" 400]
                                  ["admin UI, via redirect"   "/"                nil]
                                  ["unknown path"             "/no-such-path"    404]
                                  ["admin API (as before)"    "/admin/me"        401]]]
        (testing desc
          (let [resp (get-headers path)]
            (when status
              (is (= status (:status resp))))
            (is (= expected-headers (:headers resp))))))
      (finally
        (component/stop sys')))))

(deftest no-headers-by-default-test
  (let [sys  (support/test-system)
        sys' (component/start sys)]
    (try
      (doseq [path ["/health" "/xapi/about" "/" "/admin/me"]]
        (testing path
          (is (= {} (:headers (get-headers path))))))
      (finally
        (component/stop sys')))))
