(ns lrsql.spec.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]
            [xapi-schema.spec :as xs]))

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
;; passhash format may vary by password lib
;; Input passhash is not nilable
(s/def :lrsql.spec.admin.input/passhash string?)
;; Ret passhash (from SQL) is nilable
(s/def :lrsql.spec.admin.ret/passhash (s/nilable string?))
(s/def ::uuid ::xs/uuid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def admin-params-spec
  (s/keys :req-un [::username
                   ::password]))

(def admin-delete-params-spec
  (s/keys :req-un [::account-id]))

(def insert-admin-input-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   :lrsql.spec.admin.input/passhash]))

(def query-validate-admin-input-spec
  (s/keys :req-un [::username
                   ::password]))

(def admin-id-input-spec
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

(s/def :lrsql.spec.admin.delete/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error})))

(def delete-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.delete/result]))

(s/def :lrsql.spec.admin.query/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error
                    :lrsql.admin/invalid-password-error})))

(def query-admin-ret-spec
  (s/keys :req-un [::account-id
                   :lrsql.spec.admin.ret/passhash]))

(def query-all-admin-accounts-ret-spec
  (s/every (s/keys :req-un [::account-id
                            ::username])))

(def query-validate-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.query/result]))
