(ns lrsql.spec.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-backend?
  [bk]
  (satisfies? bp/AdminAccountBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::account-id ::c/primary-key)
(s/def ::username string?)
(s/def ::password string?)
(s/def ::passhash string?) ; format may vary by password lib

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def admin-params-spec
  (s/keys :req-un [::username
                   ::password]))

(def insert-admin-input-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   ::passhash]))

(def query-validate-admin-input-spec
  (s/keys :req-un [::username
                   ::password]))

(def delete-admin-input-spec
  (s/keys :req-un [::account-id]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :lrsql.spec.admin.insert/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/existing-account-error})))

(def insert-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.insert/result]))

(s/def :lrsql.spec.admin.delete/result uuid?)

(def delete-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.delete/result]))

(s/def :lrsql.spec.admin.query/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error
                    :lrsql.admin/invalid-password-error})))

(def query-validate-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.query/result]))
