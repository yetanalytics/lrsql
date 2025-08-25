(ns lrsql.test-runner
  (:require [lrsql.test-support :as support]
            [cognitect.test-runner.api :as runner]))

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
                      "maria" support/fresh-maria-fixture)]
      (runner/test (merge {:dirs ["src/test"]}
                          (when ns {:nses [(symbol ns)]
                                    :patterns [#"nonsensestring"]}))))))
