(ns lrsql.ops.command.auth
  (:require [lrsql.functions :as f]))

(defn insert-credentials!
  [tx inputs]
  (doall (map (partial f/insert-credential! tx) inputs)))
