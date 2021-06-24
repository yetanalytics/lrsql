(ns lrsql.spec.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.common :as c]))

(s/def ::account-id ::c/primary-key)
(s/def ::username string?)
(s/def ::password string?)
(s/def ::password-hash string?) ; TODO: Hex string
(s/def ::password-salt string?) ; TODO: Hex string

(def admin-insert-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   ::password-hash
                   ::password-salt]))

(def admin-query-spec
  (s/keys :req-un [::username]))

(def admin-delete-spec
  (s/keys :req-un [::account-id]))
