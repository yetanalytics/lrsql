(ns lrsql.util.auth-log
  "Authentication event logging.

   One interceptor, first in the webserver's chain, logs on :leave from what is
   already on the context: the route name, the response status, the request's
   :remote-addr, the identity the route's interceptors established, and the
   reason code a refusing interceptor recorded under ::reason. It never reads a
   request header's value, a body, a token, a password or an API key or secret.

   Successes are INFO, refusals WARN. Successful xAPI requests are not logged."
  (:require [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [io.pedestal.interceptor :refer [interceptor]])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def logger-name "lrsql.auth")

(defn refuse
  "Record why `ctx` is being refused, for the authentication log."
  [ctx reason]
  (assoc ctx ::reason reason))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Values
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private max-len 64)

(defn clean
  "Printable ASCII only (0x20-0x7E), with `\"` and `\\` replaced by `_` so a
   quoted value cannot end early, cut to 64 characters. nil becomes \"-\"."
  [x]
  (if (nil? x)
    "-"
    (let [s (->> (str x)
                 (filter #(<= 0x20 (int %) 0x7E))
                 (map #(if (#{\" \\} %) \_ %))
                 (take max-len)
                 (apply str))]
      (if (empty? s) "-" s))))

(defn session-tag
  "The first 8 hex digits of the SHA-256 of a session id, never the id."
  [sid]
  (if (string? sid)
    (let [d (.digest (MessageDigest/getInstance "SHA-256")
                     (.getBytes ^String sid StandardCharsets/UTF_8))]
      (apply str (map #(format "%02x" (bit-and % 0xff)) (take 4 d))))
    "-"))

(def ^:private uuid-re
  #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

(defn- uuid-str
  [x]
  (let [s (some-> x str)]
    (if (and s (re-matches uuid-re s)) s "-")))

(defn- route-str
  [route-name]
  (if (keyword? route-name)
    (clean (subs (str route-name) 1))
    "-"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Keys of other namespaces, written out so this namespace requires none of them.
(def ^:private jwt-data-key      :lrsql.admin.interceptors.jwt/data)
(def ^:private account-data-key  :lrsql.admin.interceptors.account/data)
(def ^:private oidc-identity-key :lrsql.admin.interceptors.oidc/admin-identity)
(def ^:private xapi-identity-key :com.yetanalytics.lrs.auth/identity)

(defn- oidc-account
  "The OIDC username and issuer as two keys, each cleaned and quoted (a cleaned
   value can hold spaces)."
  [{:keys [username oidc-issuer]}]
  (str "account=\"" (clean username) "\" issuer=\"" (clean oidc-issuer) "\""))

(defn- admin-identity
  "[method account-keys] for an authenticated admin request, or nil.
   account-keys is `account=<uuid>`, or for OIDC
   `account=\"<username>\" issuer=\"<issuer>\"`."
  [ctx]
  (let [{:keys [account-id one-time-id]} (get ctx jwt-data-key)
        oidc (get ctx oidc-identity-key)]
    (cond
      oidc        ["oidc" (oidc-account oidc)]
      one-time-id ["onetime" (str "account=" (uuid-str account-id))]
      account-id  ["local" (str "account=" (uuid-str account-id))])))

(defn- xapi-credential
  "The credential id the xAPI identity carries as its authority account name,
   when it is a UUID; never the identity's key or secret."
  [ctx]
  (uuid-str (get-in ctx [xapi-identity-key :agent "account" "name"])))

(def ^:private admin-reasons
  #{:invalid-token :expired :blocked :session-ended :account-missing
    :issuer-mismatch :oidc-claims :oidc-scope :oidc-required :keyset-error})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Lines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- admin-refused
  [ctx route src status]
  (let [reason (get ctx ::reason)]
    [:warn (format "AUTH event=admin.request outcome=refused route=%s reason=%s src=%s status=%s"
                   route
                   (name (if (admin-reasons reason) reason :invalid-token))
                   src status)]))

(defn event-line
  "The [level line] to log for the finished request `ctx`, or nil."
  [ctx]
  (let [route-name (get-in ctx [:route :route-name])
        route      (route-str route-name)
        status     (get-in ctx [:response :status])
        src        (clean (get-in ctx [:request :remote-addr]))
        path       (str (get-in ctx [:request :path-info]
                                (get-in ctx [:request :uri])))
        admin?     (cstr/starts-with? path "/admin")
        jwt-data   (get ctx jwt-data-key)
        reason     (get ctx ::reason)]
    (when (int? status)
      (case route-name
        :lrsql.admin.account/login
        (cond
          (= 200 status)
          [:info (format "AUTH event=admin.login outcome=success account=%s method=local src=%s status=%s"
                         (uuid-str (get-in ctx [account-data-key :account-id]))
                         src status)]
          (and (= 401 status) (= :invalid-credentials reason))
          [:warn (format "AUTH event=admin.login outcome=failure user=\"%s\" reason=invalid-credentials src=%s status=%s"
                         (clean (get-in ctx [account-data-key :username]))
                         src status)]
          (= 401 status)
          (admin-refused ctx route src status))

        :lrsql.admin.account/renew
        (cond
          (= 200 status)
          [:info (format "AUTH event=admin.renew outcome=success account=%s session=%s src=%s status=%s"
                         (uuid-str (:account-id jwt-data))
                         (session-tag (:session-id jwt-data))
                         src status)]
          (and (= 401 status) (= :refresh-expired reason))
          [:warn (format "AUTH event=admin.renew outcome=failure account=%s reason=refresh-expired src=%s status=%s"
                         (uuid-str (:account-id jwt-data))
                         src status)]
          (= 401 status)
          (admin-refused ctx route src status))

        :lrsql.admin.account/logout
        (cond
          (= 200 status)
          [:info (format "AUTH event=admin.logout outcome=success %s session=%s src=%s status=%s"
                         (or (second (admin-identity ctx)) "account=-")
                         (session-tag (:session-id jwt-data))
                         src status)]
          (= 401 status)
          (admin-refused ctx route src status))

        ;; Every other route
        (cond
          (and admin? (<= 200 status 299))
          (when-let [[method account-keys] (admin-identity ctx)]
            [:info (format "AUTH event=admin.request outcome=success route=%s %s method=%s src=%s status=%s"
                           route account-keys method src status)])

          (and admin? (#{401 403} status))
          (admin-refused ctx route src status)

          (and (not admin?) (#{401 403} status))
          [:warn (format "AUTH event=xapi.request outcome=refused reason=%s credential=%s src=%s status=%s"
                         (cond
                           (= 403 status) "scope"
                           (some? (get-in ctx [:request :headers "authorization"]))
                           "invalid-credentials"
                           :else "no-credentials")
                         (xapi-credential ctx)
                         src status)])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit!
  [[level line]]
  (log/log logger-name level nil line))

(def auth-log-interceptor
  "Logs one line per authentication event on :leave. Any exception while doing
   so is swallowed: logging never changes a response."
  (interceptor
   {:name ::auth-log
    :leave
    (fn auth-log [ctx]
      (try
        (when-let [event (event-line ctx)]
          (emit! event))
        (catch Throwable _ nil))
      ctx)}))
