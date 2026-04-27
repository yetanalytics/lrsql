(ns lrsql.test-runner
  (:require [cognitect.test-runner.api :as runner]
            [lrsql.test-support :as support]
            [clj-test-containers.core :as tc]
            [clojure.tools.logging :as log]))

(defn -main
  [& args]
  (let [{db "--database"
         ns "--ns"
         :or {db "sqlite"
              ns nil}} args]
    (when (contains? #{"postgres"
                       "mariadb"
                       "mysql"}
                     db)
      (log/infof "Starting container for %s..." db))
    (with-redefs [support/fresh-db-fixture
                  (case db
                    "sqlite"   support/fresh-sqlite-fixture
                    "postgres" support/fresh-postgres-fixture
                    "mariadb"  support/fresh-mariadb-fixture
                    "mysql"    support/fresh-mysql-fixture)
                  support/*container*
                  (case db
                    "sqlite"   nil
                    "postgres" (tc/start! support/postgres-container)
                    "mariadb"  (tc/start! support/mariadb-container)
                    "mysql"    (tc/start! support/mysql-container))]
      (try
        (when support/*container*
          (log/infof "Container for %s started!" db))
        ;; Run tests
        (runner/test (merge
                      {:dirs ["src/test"]}
                      (when ns
                        {:nses [(symbol ns)]})))
        ;; Cleanup
        (finally
          (when support/*container*
            (log/infof "Stopping container for %s..." db)
            (tc/stop! support/*container*)
            (log/infof "%s container stopped. Cleaning up..." db)
            (tc/perform-cleanup!)
            (log/info "tc cleanup complete."))
          (shutdown-agents))))))
