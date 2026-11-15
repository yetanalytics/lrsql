(ns lrsql.admin.token-id-test
  "Tests that every admin token issued by a running server carries its own
   random token ID, and that a signing key the JVM's security provider cannot
   use stops startup."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as cstr]
            [babashka.curl :as curl]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support]
            [lrsql.util :as u])
  (:import [java.util Base64]))

(use-fixtures :once support/instrumentation-fixture)

(use-fixtures :each support/fresh-db-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private test-secret
  ;; Generated per run and never printed.
  (str (random-uuid) (random-uuid)))

(def ^:private content-type {"Content-Type" "application/json"})

(defn- auth [jwt]
  (merge content-type {"Authorization" (str "Bearer " jwt)}))

(defn- login [body]
  (-> (curl/post "http://0.0.0.0:8080/admin/account/login"
                 {:headers content-type :body body})
      :body u/parse-json (get "json-web-token")))

(defn- renew [jwt]
  (-> (curl/get "http://0.0.0.0:8080/admin/account/renew"
                {:headers (auth jwt)})
      :body u/parse-json (get "json-web-token")))

(defn- csv-token [jwt]
  (-> (curl/get "http://0.0.0.0:8080/admin/csv/auth"
                {:headers (auth jwt)})
      :body u/parse-json (get "json-web-token")))

(defn- me [jwt]
  (:status (curl/get "http://0.0.0.0:8080/admin/me"
                     {:headers (auth jwt) :throw false})))

(defn- claims [jwt]
  (-> (re-find #"^[^.]+\.([^.]+)\." jwt)
      second
      (->> (.decode (Base64/getUrlDecoder)))
      u/parse-json))

(defn- seed-body [sys]
  (let [{:keys [admin-user-default admin-pass-default]}
        (get-in sys [:lrs :config])]
    (u/write-json-str {"username" admin-user-default
                       "password" admin-pass-default})))

(defn- start [secret]
  (component/start
   (support/test-system
    :conf-overrides {[:webserver :jwt-common-secret] secret})))

(defn- cause-messages
  "The messages of `e` and of every exception in its cause chain."
  [^Throwable e]
  (->> e (iterate #(.getCause ^Throwable %)) (take-while some?) (map ex-message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest token-id-test
  (let [sys  (start test-secret)
        body (seed-body sys)]
    (try
      (let [a    (login body)
            b    (login body)
            a1   (renew a)
            a2   (renew a)
            csv  (csv-token a)
            toks [a b a1 a2 csv]
            jtis (map #(get (claims %) "jti") toks)]
        (testing "sign-in, renewal and one-time tokens each carry a token ID"
          (is (every? #(and (string? %) (re-matches #"[A-Za-z0-9_-]{22}" %))
                      jtis)))
        (testing "every token and token ID differs, without waiting between
                  renewals of one session"
          (is (= 5 (count (set toks))))
          (is (= 5 (count (set jtis)))))
        (testing "renewals stay in the sign-in's session"
          (is (= #{(get (claims a) "sid")}
                 (set (map #(get (claims %) "sid") [a a1 a2])))))
        (testing "the tokens are accepted"
          (is (= [200 200 200 200] (mapv me [a b a1 a2])))))
      (finally
        (component/stop sys)))))

(deftest empty-signing-key-test
  (testing "an empty LRSQL_JWT_COMMON_SECRET stops startup"
    (let [e (try (let [sys (start "")]
                   (component/stop sys)
                   nil)
                 (catch clojure.lang.ExceptionInfo e
                   (some-> (:system (ex-data e)) component/stop)
                   e))]
      (is (some? e))
      (is (some #(and % (cstr/includes? % "LRSQL_JWT_COMMON_SECRET"))
                (cause-messages e))))))
