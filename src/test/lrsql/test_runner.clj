(ns lrsql.test-runner
  (:require [cognitect.test-runner.api :as runner]
            [lrsql.test-support :as support]
            [clj-test-containers.core :as tc]))

(defn -main
  [& args]
  (let [{db "--database"
         ns "--ns"
         :or {db "sqlite"
              ns nil}} args]

    (with-redefs [support/fresh-db-fixture
                  (case db
                    "sqlite"   support/fresh-sqlite-fixture
                    "postgres" support/fresh-postgres-fixture
                    "mariadb"  support/fresh-mariadb-fixture)]
      (try
        (binding [support/*postgres-container*
                  (case db
                    "sqlite" support/*postgres-container*
                    "postgres" (tc/start! support/*postgres-container*)
                    "mariadb" support/*mariadb-container*)
                  support/*mariadb-container*
                  (case db
                    "sqlite" support/*postgres-container*
                    "postgres" support/*postgres-container*
                    "mariadb" (tc/start! support/*mariadb-container*))]
          (runner/test (merge
                        {:dirs ["src/test"]}
                        (when ns
                          {:nses [(symbol ns)]}))))
        (finally
          (when (= "postgres" db)
            (tc/stop! support/*postgres-container*))
          (when (= "mariadb" db)
            (tc/stop! support/*mariadb-container*)))))))
