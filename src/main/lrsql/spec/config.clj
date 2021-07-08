(ns lrsql.spec.config
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [lrsql.spec.util :refer [remove-nil-vals]]))

;; TODO: Add SQLite and Postgres at the very least
(s/def ::db-type #{"h2" "h2:mem"})
(s/def ::db-name string?)
(s/def ::db-host string?)
(s/def ::db-port nat-int?)
(s/def ::db-schema string?)

(s/def ::db-properties
  (s/and string? (partial re-matches #"(?:(?:\w+:\w+)(?:,\w+:\w+)*)?")))
(s/def ::db-jdbc-url ::xs/iri)

(s/def ::database
  (s/and (s/conformer remove-nil-vals)
         (s/or :no-jdbc-url
               (s/keys :req-un [::db-type
                                ::db-name
                                ::db-host
                                ::db-port]
                       :opt-un [::db-properties
                                ::db-user
                                ::db-password])
               :jdbc-url
               (s/keys :req-un [::db-jdbc-url]
                       :opt-un [::db-user
                                ::db-password]))))

(s/def ::conn-user string?)
(s/def ::conn-password string?)
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

(s/def ::api-key-default (s/nilable string?))
(s/def ::api-secret-default (s/nilable string?))

(s/def ::stmt-more-url-prefix string?)
(s/def ::stmt-get-default pos-int?)
(s/def ::stmt-get-max pos-int?)

(s/def ::lrs
  (s/and (s/conformer remove-nil-vals)
         (s/keys :req-un [::database
                          ::api-key-default
                          ::api-secret-default
                          ::stmt-more-url-prefix
                          ::stmt-get-default
                          ::stmt-get-max])))

(s/def ::http? boolean?)
(s/def ::http2? boolean?)

(s/def ::http-host string?)
(s/def ::http-port nat-int?)
(s/def ::ssl-port nat-int?)

(s/def ::jwt-expiration-time pos-int?)
(s/def ::jwt-expiration-leeway nat-int?)

(s/def ::key-file string?) ; TODO: correct file extension/path?
(s/def ::key-alias string?)
(s/def ::key-password string?)

(s/def ::webserver ; no nilable values here
  (s/keys :req-un [::http-host
                   ::http-port
                   ::ssl-port
                   ::http2?
                   ::key-file
                   ::key-alias
                   ::key-password
                   ::jwt-expiration-time
                   ::jwt-expiration-leeway]))

(def config-spec
  (s/keys :req-un [::database
                   ::connection
                   ::lrs
                   ::webserver]))
