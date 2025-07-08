(ns lrsql.util.config-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :as su]))

(deftest config-var-redact-test
  (testing "Config var redaction"
    (is (= {:db-type     "sqlite"
            :db-name     ":memory:"
            :db-password "[REDACTED]"}
           (su/redact-config-vars
            {:db-type     "sqlite"
             :db-name     ":memory:"
             :db-password "swordfish"})))
    (is (= {:database {:db-type     "sqlite"
                       :db-name     ":memory:"
                       :db-password "[REDACTED]"}
            :admin-user-default "user-default"
            :admin-pass-default "[REDACTED]"
            :api-key-default    "api-key-default"
            :api-secret-default "[REDACTED]"}
           (su/redact-config-vars
            {:database {:db-type     "sqlite"
                        :db-name     ":memory:"
                        :db-password "swordfish"}
             :admin-user-default "user-default"
             :admin-pass-default "pass-default"
             :api-key-default    "api-key-default"
             :api-secret-default "api-secret-default"})))
    (testing "in a server map"
      (is (= {:io.pedestal.http/port 8080
              :io.pedestal.http/host "0.0.0.0"
              :io.pedestal.http/type :jetty
              :io.pedestal.http/container-options
              {:h2c? true
               :h2? true
               :ssl? true
               :ssl-port 8443
               :keystore {}
               :key-password "[REDACTED]"}}
             (su/redact-config-vars
              {:io.pedestal.http/port 8080
               :io.pedestal.http/host "0.0.0.0"
               :io.pedestal.http/type :jetty
               :io.pedestal.http/container-options
               {:h2c? true
                :h2? true
                :ssl? true
                :ssl-port 8443
                :keystore {}
                :key-password "this-is-my-oh-so-secure-password"}}))))
    (testing "on keywords"
      (is (= {:db-type     "sqlite"
              :db-name     ":memory:"
              :db-password :redacted}
             (su/redact-config-vars
              {:db-type     "sqlite"
               :db-name     ":memory:"
               :db-password :swordfish}))))
    (testing "on symbols"
      (is (= {:db-type     "sqlite"
              :db-name     ":memory:"
              :db-password 'redacted}
             (su/redact-config-vars
              {:db-type     "sqlite"
               :db-name     ":memory:"
               :db-password 'swordfish}))))
    (testing "not on ints"
      (is (= {:db-type     "sqlite"
              :db-name     ":memory:"
              :db-password 100}
             (su/redact-config-vars
              {:db-type     "sqlite"
               :db-name     ":memory:"
               :db-password 100}))))
    (testing "propgates through spec errors"
      (is (= {::s/problems '({:path [:no-jdbc-url :db-type]
                              :pred #{"sqlite"
                                      "postgres"
                                      "postgresql"
                                      "mariadb"}
                              :val  "AS400"
                              :via  [::cs/database ::cs/db-type]
                              :in   [:db-type]}
                             {:path [:jdbc-url]
                              :pred (clojure.core/fn [%]
                                      (clojure.core/contains? % :db-jdbc-url))
                              :val {:db-type     "AS400"
                                    :db-name     ":memory:"
                                    :db-password "[REDACTED]"}
                              :via [::cs/database]
                              :in []})
              ::s/spec ::cs/database
              ::s/value {:db-type     "AS400"
                         :db-name     ":memory:"
                         :db-password "[REDACTED]"}}
             (s/explain-data ::cs/database
                             (su/redact-config-vars
                              {:db-type     "AS400"
                               :db-name     ":memory:"
                               :db-password "swordfish"})))))))
