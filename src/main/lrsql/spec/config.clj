(ns lrsql.spec.config
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as cstr]
            [xapi-schema.spec :as xs]
            [lrsql.spec.util :as u]))

(s/def ::db-type #{"sqlite" "postgres" "postgresql"})
(s/def ::db-name string?)
(s/def ::db-host string?)
(s/def ::db-port nat-int?)

(def db-prop-regex
  "Regex for JDBC URL query params."
  #"(?:(?:[^&]+=[^&]+)(?:&[^&]+=[^&]+)*)?")

(s/def ::db-properties (s/and string? (partial re-matches db-prop-regex)))
(s/def ::db-jdbc-url ::xs/iri)

(s/def ::db-user string?)
(s/def ::db-password string?)
(s/def ::db-schema string?)
(s/def ::db-catalog string?)

;; This is used in testing only, not configurable for production
(s/def ::test-db-version string?)

(s/def ::database
  (s/and (s/conformer u/remove-nil-vals)
         (s/conformer u/remove-neg-vals)
         (s/or :no-jdbc-url
               (s/keys :req-un [::db-type
                                ::db-name]
                       :opt-un [::db-host
                                ::db-port
                                ::db-properties
                                ::db-user
                                ::db-password
                                ::db-schema
                                ::db-catalog
                                ::test-db-version])
               :jdbc-url
               (s/keys :req-un [::db-jdbc-url]
                       :opt-un [::db-user
                                ::db-password
                                ::db-schema
                                ::db-catalog
                                ::test-db-version]))))

(s/def ::pool-auto-commit boolean?)
(s/def ::pool-initialization-fail-timeout int?)
(s/def ::pool-minimum-idle nat-int?)
(s/def ::pool-maximum-size pos-int?)
(s/def ::pool-isolate-internal-queries boolean?)
(s/def ::pool-name string?)
(s/def ::pool-enable-jmx boolean?)

(s/def ::pool-connection-timeout
  (s/and pos-int? (partial <= 250)))
(s/def ::pool-validation-timeout
  (s/and pos-int? (partial <= 250)))

(s/def ::pool-keepalive-time
  (s/or :disabled zero?
        :enabled (s/and pos-int? (partial <= 10000))))
(s/def ::pool-idle-timeout
  (s/or :removal-disabled zero?
        :removal-enabled (s/and pos-int? (partial <= 10000))))
(s/def ::pool-max-lifetime
  (s/or :no-max-lifetime zero?
        :max-lifetime (s/and pos-int? (partial <= 30000))))
(s/def ::pool-leak-detection-threshold
  (s/or :disabled zero?
        :enabled (s/and pos-int? (partial <= 2000))))

(s/def ::pool-transaction-isolation
  #{"TRANSACTION_NONE" ; This level exists but will cause problems
    "TRANSACTION_READ_UNCOMMITTED"
    "TRANSACTION_READ_COMMITTED"
    "TRANSACTION_REPEATABLE_READ"
    "TRANSACTION_SERIALIZABLE"})

(s/def ::connection
  (s/and (s/conformer u/remove-nil-vals)
         (s/keys :req-un [::database
                          ::pool-auto-commit
                          ::pool-keepalive-time
                          ::pool-connection-timeout
                          ::pool-idle-timeout
                          ::pool-validation-timeout
                          ::pool-initialization-fail-timeout
                          ::pool-max-lifetime
                          ::pool-minimum-idle
                          ::pool-maximum-size
                          ::pool-isolate-internal-queries
                          ::pool-leak-detection-threshold
                          ::pool-enable-jmx]
                 :opt-un [::pool-name
                          ::pool-transaction-isolation])
         (fn keepalive-lt-max-lifetime?
           [conn-config]
           ;; Need to call `second` due to `s/or` conforming the key values.
           (let [keep-alive (-> conn-config :pool-keepalive-time second)
                 max-life   (-> conn-config :pool-max-lifetime second)]
             (or (= 0 keep-alive) ; keepalive time is disabled
                 (= 0 max-life)   ; max lifetime is infinite
                 (< keep-alive max-life))))
         (fn validation-lt-connection-timeout?
           [{:keys [pool-validation-timeout pool-connection-timeout]}]
           (< pool-validation-timeout pool-connection-timeout))))

(defn- prefix? [s]
  (cstr/starts-with? s "/"))

(defn- not-admin-prefix? [s]
  (not (cstr/starts-with? s "/admin")))

(s/def ::stmt-url-prefix (s/and string? prefix? not-admin-prefix?))

(s/def ::admin-user-default string?)
(s/def ::admin-pass-default string?)

(s/def ::api-key-default string?)
(s/def ::api-secret-default string?)

(s/def ::stmt-get-default pos-int?)
(s/def ::stmt-get-max pos-int?)
(s/def ::stmt-get-max-csv pos-int?)

(s/def ::authority-template string?)
(s/def ::authority-url ::xs/irl)
(s/def ::oidc-authority-template string?)
(s/def ::oidc-scope-prefix string?)

(s/def ::enable-reactions boolean?)
(s/def ::reaction-buffer-size pos-int?)

(s/def ::supported-versions
  (s/and
   (s/conformer
    (fn [x]
      (if (string? x)
        (into #{}
              (cstr/split x #","))
        x)))
   (s/coll-of
    #{"1.0.3" "2.0.0"}
    :kind set?
    :into #{}
    :min-count 1)))

(s/def ::enable-strict-version boolean?)

(s/def ::lrs
  (s/and (s/conformer u/remove-nil-vals)
         (s/conformer u/remove-neg-vals)
         (s/keys :req-un [::stmt-get-default
                          ::stmt-get-max
                          ::stmt-url-prefix
                          ::authority-template
                          ::authority-url
                          ::oidc-authority-template
                          ::oidc-scope-prefix
                          ::enable-reactions
                          ::reaction-buffer-size
                          ::supported-versions
                          ::enable-strict-version]
                 :opt-un [::admin-user-default
                          ::admin-pass-default
                          ::api-key-default
                          ::api-secret-default
                          ::stmt-get-max-csv])))

(s/def ::enable-http boolean?)
(s/def ::enable-http2 boolean?)

(s/def ::http-host string?)
(s/def ::http-port nat-int?)
(s/def ::ssl-port nat-int?)
(s/def ::url-prefix ::stmt-url-prefix)

(s/def ::allow-all-origins boolean?)
(s/def ::allowed-origins (s/nilable (s/coll-of string?)))

(s/def ::jwt-exp-time pos-int?)
(s/def ::jwt-exp-leeway nat-int?)
(s/def ::jwt-refresh-exp-time pos-int?)
(s/def ::jwt-refresh-interval pos-int?)
(s/def ::jwt-interaction-window pos-int?)
(s/def ::jwt-no-val boolean?)
(s/def ::jwt-no-val-uname (s/nilable string?))
(s/def ::jwt-no-val-issuer (s/nilable string?))
(s/def ::jwt-no-val-role-key (s/nilable string?))
(s/def ::jwt-no-val-role (s/nilable string?))
(s/def ::jwt-no-val-logout-url (s/nilable string?))
(s/def ::jwt-common-secret (s/nilable string?))

(s/def ::key-file string?) ; TODO: correct file extension/path?
(s/def ::key-alias string?)
(s/def ::key-password string?)

(s/def ::key-pkey-file string?)
(s/def ::key-cert-chain string?)
(s/def ::key-enable-selfie boolean?)

(s/def ::enable-admin-ui boolean?)
(s/def ::enable-admin-status boolean?)
(s/def ::enable-stmt-html boolean?)

(s/def ::proxy-path (s/nilable string?))

(s/def ::oidc-issuer (s/nilable string?))
(s/def ::oidc-audience (s/nilable string?))
(s/def ::oidc-client-id (s/nilable string?))
(s/def ::oidc-client-template string?)
(s/def ::oidc-verify-remote-issuer boolean?)
(s/def ::oidc-enable-local-admin boolean?)

(s/def ::auth-by-cred-id boolean?)

(s/def ::sec-head-hsts (s/nilable string?))
(s/def ::sec-head-frame (s/nilable string?))
(s/def ::sec-head-content-type (s/nilable string?))
(s/def ::sec-head-xss (s/nilable string?))
(s/def ::sec-head-download (s/nilable string?))
(s/def ::sec-head-cross-domain (s/nilable string?))
(s/def ::sec-head-content (s/nilable string?))

(s/def ::enable-clamav boolean?)
(s/def ::clamav-host string?)
(s/def ::clamav-port nat-int?)

(s/def ::webserver
  (s/and
   (s/keys :req-un [::http-host
                    ::http-port
                    ::ssl-port
                    ::enable-http
                    ::enable-http2
                    ::allow-all-origins
                    ::allowed-origins
                    ::url-prefix
                    ::proxy-path
                    ::key-alias
                    ::key-password
                    ::key-enable-selfie
                    ::jwt-exp-time
                    ::jwt-exp-leeway
                    ::jwt-refresh-exp-time
                    ::jwt-refresh-interval
                    ::jwt-interaction-window
                    ::jwt-no-val
                    ::enable-admin-ui
                    ::enable-admin-status
                    ::admin-language
                    ::enable-stmt-html
                    ::oidc-verify-remote-issuer
                    ::oidc-client-template
                    ::oidc-enable-local-admin
                    ::enable-clamav
                    ::clamav-host
                    ::clamav-port]
           :opt-un [::key-file
                    ::key-pkey-file
                    ::key-cert-chain
                    ::jwt-no-val-uname
                    ::jwt-no-val-issuer
                    ::jwt-no-val-role
                    ::jwt-no-val-role-key
                    ::jwt-no-val-logout-url
                    ::jwt-common-secret
                    ::sec-head-hsts
                    ::sec-head-frame
                    ::sec-head-content-type
                    ::sec-head-xss
                    ::sec-head-download
                    ::sec-head-cross-domain
                    ::sec-head-content
                    ::oidc-issuer
                    ::oidc-audience
                    ::oidc-client-id
                    ::auth-by-cred-id])
   ;; conditional validation for presence of no-val supporting config
   (fn [{:keys [jwt-no-val jwt-no-val-uname jwt-no-val-issuer
                jwt-no-val-role-key jwt-no-val-role]}]
     (if jwt-no-val
       (and jwt-no-val-uname jwt-no-val-issuer jwt-no-val-role-key
            jwt-no-val-role)
       true))
   ;; validation for JWT temporal intervals
   (fn [{:keys [jwt-exp-time jwt-refresh-interval jwt-interaction-window]}]
     (and (<= jwt-interaction-window jwt-refresh-interval)
          (< jwt-refresh-interval jwt-exp-time)))))

(s/def ::tuning
  (s/keys :opt-un [::enable-jsonb]))

(def config-spec
  (s/keys :req-un [::connection
                   ::lrs
                   ::webserver
                   ::tuning]))
