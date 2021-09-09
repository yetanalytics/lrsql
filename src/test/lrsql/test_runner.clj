(ns lrsql.test-runner
  (:require [clojure.test :refer [use-fixtures]]
            [cognitect.test-runner.api :as api]
            [lrsql.test-support :as support]))

(def db-test-namespaces
  [`lrsql.admin.protocol-test
   `lrsql.admin.route-test
   `lrsql.conformance-test
   `lrsql.https-test
   `lrsq.lrs-test])

(defn set-fixtures!
  [fix]
  (dorun (map (fn set-fixture! [nmsp]
                (binding [*ns* (create-ns nmsp)]
                  (use-fixtures :each fix)))
              db-test-namespaces)))

(defn -main
  [& args]
  (let [{db "--database" :or {db "h2"}} args
        fix (case db
              "h2"       support/fresh-h2-fixture
              "sqlite"   support/fresh-sqlite-fixture
              "postgres" support/fresh-postgres-fixture)]
    #_(print fix)
    (set-fixtures! fix)
    (api/test {:dirs ["src/test/lrsql/admin"]})))


(comment
  (-main "--database" "sqlite")
  (meta (create-ns `lrsql.admin.protocol-test))
  )