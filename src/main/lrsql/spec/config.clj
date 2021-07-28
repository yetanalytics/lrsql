(ns lrsql.spec.config
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.spec.util :refer [remove-nil-vals]]))

(s/def ::db-type #{"h2" "h2:mem" "sqlite" "postgres" "postgresql"})
(s/def ::db-name string?)
(s/def ::db-host string?)
(s/def ::db-port nat-int?)

(s/def ::db-properties
  (s/and string? (partial re-matches #"(?:(?:\w+:\w+)(?:,\w+:\w+)*)?")))
(s/def ::db-jdbc-url ::xs/iri)

(s/def ::db-user string?)
(s/def ::db-password string?)

(s/def ::database
  (s/and (s/conformer remove-nil-vals)
         (s/or :no-jdbc-url
               (s/keys :req-un [::db-type
                                ::db-name]
                       :opt-un [::db-properties
                                ::db-host
                                ::db-port
                                ::db-user
                                ::db-password])
               :jdbc-url
               (s/keys :req-un [::db-jdbc-url]
                       :opt-un [::db-user
                                ::db-password]))))

(s/def ::pool-init-size nat-int?)
(s/def ::pool-min-size nat-int?)
(s/def ::pool-inc nat-int?)
(s/def ::pool-max-size nat-int?)
(s/def ::pool-max-stmts nat-int?)

(s/def ::connection
  (s/and (s/conformer remove-nil-vals)
         (s/keys :req-un [::database]
                 :opt-un [::pool-init-size
                          ::pool-min-size
                          ::pool-inc
                          ::pool-max-size
                          ::pool-max-stmts])
         (fn [{:keys [pool-min-size pool-max-size]
               :or {pool-min-size 3 ; c3p0 defaults
                    pool-max-size 15}}]
           (<= pool-min-size pool-max-size))))

(s/def ::api-key-default string?)
(s/def ::api-secret-default string?)

(s/def ::stmt-more-url-prefix string?)
(s/def ::stmt-get-default pos-int?)
(s/def ::stmt-get-max pos-int?)

(s/def ::lrs
  (s/and (s/conformer remove-nil-vals)
         (s/keys :req-un [::stmt-more-url-prefix
                          ::stmt-get-default
                          ::stmt-get-max]
                 :opt-un [::api-key-default
                          ::api-secret-default])))

(s/def ::http? boolean?)
(s/def ::http2? boolean?)

(s/def ::http-host string?)
(s/def ::http-port nat-int?)
(s/def ::ssl-port nat-int?)

(s/def ::jwt-exp-time pos-int?)
(s/def ::jwt-exp-leeway nat-int?)

(s/def ::key-file string?) ; TODO: correct file extension/path?
(s/def ::key-alias string?)
(s/def ::key-password string?)

(s/def ::key-pkey-file string?)
(s/def ::key-cert-file string?)
(s/def ::key-ca-file string?)

(s/def ::webserver
  (s/keys :req-un [::http-host
                   ::http-port
                   ::ssl-port
                   ::http2?
                   ::key-alias
                   ::key-password
                   ::jwt-exp-time
                   ::jwt-exp-leeway]
          :opt-un [::key-file
                   ::key-pkey-file
                   ::key-cert-file
                   ::key-ca-file]))

(def config-spec
  (s/keys :req-un [::database
                   ::connection
                   ::lrs
                   ::webserver]))
