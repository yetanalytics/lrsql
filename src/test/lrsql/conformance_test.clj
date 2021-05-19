(ns lrsql.conformance-test
  (:require [clojure.test :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [clojure.data.json :as json]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]))

(defn- assert-in-mem-db
  []
  (when (not= "h2:mem" (:db-type env))
    (throw (ex-info "Test can only be run on in-memory H2 database!"
                    {:kind    ::non-mem-db
                     :db-type (:db-type env)}))))

(deftest conformance-test
  (assert-in-mem-db)
  (conf/with-test-suite
    (let [sys (system/system)
          ;; TODO: confirm that :io.pedestal.http/join? is false in pedestal
          ;; service
          sys' (component/start sys)]
      (is (conf/conformant?
           ;; TODO: match these to what you actually serve
           "-e" "http://localhost:8080/xapi" "-b" "-z"

           ;; zero in on specific tests using grep:
           ;; "-g" "XAPI-00143"

           ))
      (component/stop sys'))))
