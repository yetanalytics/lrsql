(ns lrsql.test-support
  (:require [config.core :refer [env]])
  (:import [java.util UUID]))

(defn fresh-db-fixture
  [f]
  (with-redefs [env (merge env
                           {:db-name (str (UUID/randomUUID))})]
    (f)))
