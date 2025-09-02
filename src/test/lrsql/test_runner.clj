(ns lrsql.test-runner
  (:require [cognitect.test-runner.api :as runner]
            [lrsql.test-support :as support]
            [clj-test-containers.core :as tc]))

(defn -main
  [& args]
  (let [{db "--database" :or {db "sqlite"}} args]
    (with-redefs [support/fresh-db-fixture
                  (case db
                    "sqlite"   support/fresh-sqlite-fixture
                    "postgres" support/fresh-postgres-fixture)]
      (try
        (binding [support/*postgres-container*
                  (case db
                    "sqlite" support/*postgres-container*
                    "postgres" (tc/start! support/*postgres-container*))]
          (runner/test {:dirs ["src/test"]}))
        (finally
          (when (= "postgres" db)
            (tc/stop! support/*postgres-container*)))))))
