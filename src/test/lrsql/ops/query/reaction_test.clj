(ns lrsql.ops.query.reaction-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [lrsql.ops.query.reaction :as qr]
            [lrsql.test-support :as support]
            [lrsql.test-constants :as tc]
            [com.stuartsierra.component :as component]
            [lrsql.admin.protocol :as adp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once support/instrumentation-fixture)
(use-fixtures :each support/fresh-db-fixture)

(deftest query-all-reactions-test
  (let [sys  (support/test-system)
        sys' (component/start sys)
        lrs  (-> sys' :lrs)
        bk   (:backend lrs)
        ds   (-> sys' :lrs :connection :conn-pool)]

    ;; Create an active reaction
    (adp/-create-reaction lrs tc/simple-reaction-ruleset true)
    ;; Create an inactive reaciton
    (adp/-create-reaction lrs tc/simple-reaction-ruleset false)

    (try
      (testing "Finds all reactions"
        (is (= [{:ruleset tc/simple-reaction-ruleset
                 :active  true}
                {:ruleset tc/simple-reaction-ruleset
                 :active  false}]
                 (->> (qr/query-all-reactions bk ds)
                      (map #(select-keys % [:ruleset :active]))))))
      (finally (component/stop sys')))))
