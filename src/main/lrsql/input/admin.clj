(ns lrsql.input.admin
  (:require [lrsql.util.admin :as ua]))

(defn admin-input
  [username]
  {:username username})

(defn admin-insert-input
  [username password]
  (merge (admin-input username)
         (ua/hash-password password)))
