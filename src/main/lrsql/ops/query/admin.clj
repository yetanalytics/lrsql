(ns lrsql.ops.query.admin
  (:require [lrsql.functions  :as f]))

(defn query-admin
  [tx input]
  (if-some [res (f/query-account tx input)]
    res
    (let [uname (:username input)]
      (throw (ex-info (format "Account %s does not exist!" uname)
                      {:type     ::missing-account
                       :username uname})))))
