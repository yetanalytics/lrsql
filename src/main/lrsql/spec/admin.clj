(ns lrsql.spec.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.common :as c]))

(s/def ::account-id ::c/primary-key)
(s/def ::username string?)
(s/def ::password string?)
(s/def ::passhash string?) ; format may vary by password lib

(def admin-params-spec
  (s/keys :req-un [::username
                   ::password]))

(def admin-insert-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   ::passhash]))

(def admin-query-validate-spec
  (s/keys :req-un [::username
                   ::password]))

(def admin-delete-spec
  (s/keys :req-un [::account-id]))

;; Results

(s/def :lrsql.spec.admin.insert/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/existing-account-error})))

(def admin-insert-res-spec
  (s/keys :req-un [:lrsql.spec.admin.insert/result]))

(s/def :lrsql.spec.admin.delete/result uuid?)

(def admin-delete-res-spec
  (s/keys :req-un [:lrsql.spec.admin.delete/result]))

(s/def :lrsql.spec.admin.query/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error
                    :lrsql.admin/invalid-password-error})))

(def admin-query-validate-res-spec
  (s/keys :req-un [:lrsql.spec.admin.query/result]))
