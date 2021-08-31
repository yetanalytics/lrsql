(ns lrsql.spec.config
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.spec.util :as u]))

(s/def ::db-type #{"h2" "h2:mem" "sqlite" "postgres" "postgresql"})
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
                                ::db-schema])
               :jdbc-url
               (s/keys :req-un [::db-jdbc-url]
                       :opt-un [::db-user
                                ::db-password
                                ::db-schema]))))

(s/def ::pool-auto-commit boolean?)
(s/def ::pool-init-fail-timeout int?)
(s/def ::pool-min-idle nat-int?)
(s/def ::pool-max-size pos-int?)
(s/def ::pool-name string?)

(s/def ::pool-connection-timeout
  (s/and pos-int? (partial <= 250)))
(s/def ::pool-idle-timeout
  (s/and pos-int? (partial <= 10000)))
(s/def ::pool-validation-timeout
  (s/and pos-int? (partial <= 250)))

(s/def ::pool-keepalive-time
  (s/or :disabled zero?
        :enabled (s/and pos-int? (partial <= 10000))))
(s/def ::pool-max-lifetime
  (s/or :no-max-lifetime zero?
        :max-lifetime (s/and pos-int? (partial <= 30000))))

(s/def ::connection
  (s/and (s/conformer u/remove-nil-vals)
         (s/conformer u/remove-neg-vals)
         (s/keys :req-un [::database
                          ::pool-auto-commit
                          ::pool-keepalive-time
                          ::pool-connection-timeout
                          ::pool-idle-timeout
                          ::pool-validation-timeout
                          ::pool-init-fail-timeout
                          ::pool-max-lifetime
                          ::pool-min-idle
                          ::pool-max-size]
                 :opt-un [::pool-name])
         (fn valid-keepalive-time?
           [{:keys [pool-keepalive-time pool-max-lifetime]}]
           (< pool-keepalive-time pool-max-lifetime))
         (fn valid-validation-timeout?
           [{:keys [pool-validation-timeout pool-connection-timeout]}]
           (< pool-validation-timeout pool-connection-timeout))))

(s/def ::api-key-default string?)
(s/def ::api-secret-default string?)

(s/def ::stmt-get-default pos-int?)
(s/def ::stmt-get-max pos-int?)

(s/def ::authority-template string?)
(s/def ::authority-url ::xs/irl)

(s/def ::lrs
  (s/and (s/conformer u/remove-nil-vals)
         (s/conformer u/remove-neg-vals)
         (s/keys :req-un [::stmt-get-default
                          ::stmt-get-max
                          ::stmt-url-prefix
                          ::authority-template
                          ::authority-url]
                 :opt-un [::api-key-default
                          ::api-secret-default])))

(s/def ::enable-http boolean?)
(s/def ::enable-http2 boolean?)

(s/def ::http-host string?)
(s/def ::http-port nat-int?)
(s/def ::ssl-port nat-int?)

(s/def ::jwt-exp-time pos-int?)
(s/def ::jwt-exp-leeway nat-int?)

(s/def ::key-file string?) ; TODO: correct file extension/path?
(s/def ::key-alias string?)
(s/def ::key-password string?)

(s/def ::key-pkey-file string?)
(s/def ::key-cert-chain string?)
(s/def ::key-enable-selfie boolean?)

(s/def ::enable-admin-ui boolean?)

(s/def ::webserver
  (s/keys :req-un [::http-host
                   ::http-port
                   ::ssl-port
                   ::enable-http
                   ::enable-http2
                   ::url-prefix
                   ::key-alias
                   ::key-password
                   ::key-enable-selfie
                   ::jwt-exp-time
                   ::jwt-exp-leeway
                   ::enable-admin-ui]
          :opt-un [::key-file
                   ::key-pkey-file
                   ::key-cert-chain]))

(def config-spec
  (s/keys :req-un [::connection
                   ::lrs
                   ::webserver]))
