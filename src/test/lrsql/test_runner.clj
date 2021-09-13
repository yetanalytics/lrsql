(ns lrsql.test-runner
  (:require [cognitect.test-runner.api :as api]
            [lrsql.test-support :as support]))

(defn -main
  [& args]
  (let [{db "--database" :or {db "h2"}} args]
    (with-redefs [support/fresh-db-fixture
                  (case db
                    "h2"       support/fresh-h2-fixture
                    "sqlite"   support/fresh-sqlite-fixture
                    "postgres" support/fresh-postgres-fixture)]
      (api/test {:dirs ["src/test"]}))))
