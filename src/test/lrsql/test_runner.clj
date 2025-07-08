(ns lrsql.test-runner
  (:require [cognitect.test-runner.api :as runner]
            [lrsql.test-support :as support]))

(defn -main
  [& args]
  (let [{db "--database" :or {db "sqlite"}} args]
    (with-redefs [support/fresh-db-fixture
                  (case db
                    "sqlite"   support/fresh-sqlite-fixture
                    "postgres" support/fresh-postgres-fixture
                    "maria" support/fresh-maria-fixture)]
      (runner/test {:dirs ["src/test"]}))))
