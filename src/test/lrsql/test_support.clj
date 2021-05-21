(ns lrsql.test-support
  (:require [config.core :refer [env]])
  (:import [java.util UUID]))

(defn fresh-db-fixture
  [f]
  (with-redefs
    [env (merge
          env
          {:db-name
           (str (UUID/randomUUID))})]
    (f)))

(defn assert-in-mem-db
  []
  (when (not= "h2:mem" (:db-type env))
    (throw (ex-info "Test can only be run on in-memory H2 database!"
                    {:kind    ::non-mem-db
                     :db-type (:db-type env)}))))
