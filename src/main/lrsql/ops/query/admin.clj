(ns lrsql.ops.query.admin
  (:require [lrsql.functions  :as f]))

(defn query-admin
  [tx input]
  (if-some [{account-id :id
             passhash   :passhash}
            (f/query-account tx input)]
    {:account-id account-id
     :passhash   passhash}
    :lrsql.admin/missing-account-error))
