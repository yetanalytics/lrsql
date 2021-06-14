(ns lrsql.ops.command.auth
  (:require [lrsql.functions :as f]))

(defn insert-credential!
  "Insert a credential into the DB with a particular scope."
  [tx input]
  (f/insert-credential! tx input))

(defn insert-credentials!
  "Insert a seq of credential inputs, each which may have a different scope
   (but should have the same API keys)."
  [tx inputs]
  (doall (map (partial insert-credential! tx)
              inputs)))
