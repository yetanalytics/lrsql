(ns lrsql.spec.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.common :as c]))

(s/def ::account-id ::c/primary-key)
(s/def ::username string?)
(s/def ::password string?)
(s/def ::passhash string?) ; format may vary by password lib

(def admin-insert-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   ::passhash]))

(def admin-query-spec
  (s/keys :req-un [::username]))

(def admin-delete-spec
  (s/keys :req-un [::account-id]))
