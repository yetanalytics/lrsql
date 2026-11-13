(ns lrsql.admin.session-test
  "Tests that signing out ends the whole admin login session: every token of
   that session, including ones replaced by a renewal, is refused afterwards,
   while other sessions keep working."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [babashka.curl :as curl]
            [buddy.sign.jwt :as bj]
            [com.stuartsierra.component :as component]
            [lrsql.admin.protocol :as adp]
            [lrsql.backend.protocol :as bp]
            [lrsql.mariadb.data :as md]
            [lrsql.mariadb.record :as mr]
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

(defn- status [resp] (:status resp))

(defn- me [jwt]
  (status (curl/get "http://0.0.0.0:8080/admin/me"
                    {:headers (auth jwt) :throw false})))

(defn- renew [jwt]
  (curl/get "http://0.0.0.0:8080/admin/account/renew"
            {:headers (auth jwt) :throw false}))

(defn- renew-jwt
  "Renew `jwt`. Waits just over a second first, so the new token's `iat`
   differs and it is a different token from the one it replaces."
  [jwt]
  (Thread/sleep 1100)
  (-> (renew jwt) :body u/parse-json (get "json-web-token")))

(defn- logout [jwt]
  (status (curl/post "http://0.0.0.0:8080/admin/account/logout"
                     {:headers (auth jwt) :throw false})))

(defn- claims [jwt]
  (-> (re-find #"^[^.]+\.([^.]+)\." jwt)
      second
      (->> (.decode (Base64/getUrlDecoder)))
      u/parse-json))

(defn- session-key
  "The blocklist entry that a signed-out session is stored under."
  [sid]
  (str "sid:" sid))

(defn- start []
  (component/start
   (support/test-system
    :conf-overrides {[:webserver :jwt-common-secret] test-secret})))

(defn- seed-body [sys]
  (let [{:keys [admin-user-default admin-pass-default]}
        (get-in sys [:lrs :config])]
    (u/write-json-str {"username" admin-user-default
                       "password" admin-pass-default})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest sign-out-ends-session-test
  (let [sys  (start)
        body (seed-body sys)]
    (try
      (testing "sign in, renew twice, sign out with the newest token"
        (let [t0   (login body)
              t1   (renew-jwt t0)
              t2   (renew-jwt t1)
              sids (map #(get (claims %) "sid") [t0 t1 t2])]
          (testing "- the three tokens differ and share one session ID"
            (is (= 3 (count (set [t0 t1 t2]))))
            (is (every? string? sids))
            (is (= 1 (count (set sids))))
            (is (= 1 (count (set (map #(get (claims %) "ref") [t0 t1 t2]))))))
          (testing "- all three work before sign-out"
            (is (= [200 200 200] (mapv me [t0 t1 t2]))))
          (is (= 200 (logout t2)))
          (testing "- all three are refused on an admin route"
            (is (= [401 401 401] (mapv me [t0 t1 t2]))))
          (testing "- and on renew"
            (is (= [401 401 401] (mapv (comp status renew) [t0 t1 t2]))))))
      (finally
        (component/stop sys)))))

(deftest two-sessions-test
  (let [sys  (start)
        body (seed-body sys)]
    (try
      (testing "signing out of one session leaves the other working"
        (let [a (login body)
              b (login body)]
          (is (string? (get (claims a) "sid")))
          (is (string? (get (claims b) "sid")))
          (is (not= (get (claims a) "sid") (get (claims b) "sid")))
          (is (= 200 (logout a)))
          (is (= 401 (me a)))
          (is (= 200 (me b)))
          (let [b' (renew-jwt b)]
            (is (= 200 (me b'))))))
      (finally
        (component/stop sys)))))

(deftest token-without-session-id-test
  (let [sys  (start)
        body (seed-body sys)]
    (try
      (let [t      (login body)
            claim  (-> (claims t)
                       (select-keys ["acc" "iat" "exp" "ref"]))
            sid    (get (claims t) "sid")]
        (testing "a validly signed account token with no session ID is refused"
          (is (= 401 (me (bj/sign claim test-secret)))))
        (testing "control: the same claims with the session ID are accepted"
          (is (string? sid))
          (is (= 200 (me (bj/sign (assoc claim "sid" sid) test-secret))))))
      (finally
        (component/stop sys)))))

(deftest session-eviction-test
  (let [sys  (start)
        lrs  (:lrs sys)
        body (seed-body sys)
        {:keys [jwt-exp-time jwt-exp-leeway]} (get-in sys [:webserver :config])]
    (try
      (let [t        (login body)
            sid      (get (claims t) "sid")
            ref      (u/millis->time (* 1000 (get (claims t) "ref")))
            k        (session-key sid)
            ;; The last moment a token of this session could still be
            ;; accepted, plus one second, as for a blocked token.
            evict    (u/offset-time ref (+ jwt-exp-time jwt-exp-leeway 1) :seconds)
            purge-at (fn [now]
                       (with-redefs [u/current-time (constantly now)]
                         (adp/-purge-blocklist lrs jwt-exp-leeway)))]
        (is (string? sid))
        (is (false? (adp/-jwt-blocked? lrs k)))
        (is (= 200 (logout t)))
        (testing "sign-out stored the session under its sid: key"
          (is (true? (adp/-jwt-blocked? lrs k))))
        (testing "a purge one second before the eviction time keeps it"
          ;; The purge deletes entries whose eviction time <= now - leeway
          (purge-at (u/offset-time evict (dec jwt-exp-leeway) :seconds))
          (is (true? (adp/-jwt-blocked? lrs k)))
          (is (= 401 (me t))))
        (testing "a purge at the eviction time removes it"
          (purge-at (u/offset-time evict jwt-exp-leeway :seconds))
          (is (false? (adp/-jwt-blocked? lrs k)))))
      (finally
        (component/stop sys)))))

(deftest mariadb-session-key-test
  ;; MariaDB and MySQL store a blocklist entry as its 44-character SHA-256
  ;; hash, so a session key fits the same column as a token.
  (testing "MariaDB stores a session key as its hash, like a token"
    (let [captured (atom nil)
          k        (session-key "AAAAAAAAAAAAAAAAAAAAAA")
          input    {:jwt k :eviction-time (u/current-time)}]
      ;; HugSQL defines these functions at load time.
      #_{:clj-kondo/ignore [:unresolved-var]}
      (with-redefs [mr/insert-blocked-jwt! (fn [_tx in] (reset! captured in) 1)]
        (bp/-insert-blocked-jwt! (mr/map->MariadbBackend {}) nil input))
      (is (= 44 (count (:jwt @captured))))
      (is (= (md/sha256-base64 k) (:jwt @captured)))
      (is (not= k (:jwt @captured)))))
  (testing "and looks it up by the same hash"
    (let [captured (atom nil)
          k        (session-key "BBBBBBBBBBBBBBBBBBBBBB")]
      ;; HugSQL defines these functions at load time.
      #_{:clj-kondo/ignore [:unresolved-var]}
      (with-redefs [mr/query-blocked-jwt-exists (fn [_tx in] (reset! captured in) nil)]
        (bp/-query-blocked-jwt (mr/map->MariadbBackend {}) nil {:jwt k}))
      (is (= (md/sha256-base64 k) (:jwt @captured))))))
