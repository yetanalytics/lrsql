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

;; username and password are treated as simple non-empty strings
(s/def ::username (s/and string? not-empty))
(s/def ::password (s/and string? not-empty))

;; Except on input validation!
;; Minimum lengths
(def u-min-len 7)
(def p-min-len 10)

(def digit-set (into #{} (map char (range 48 58))))  ; 0-9
(def upper-set (into #{} (map char (range 65 91))))  ; A-Z
(def lower-set (into #{} (map char (range 97 123)))) ; a-z
(def special-set #{\! \@ \# \$ \% \^ \& \* \_ \- \+ \= \?})

(s/def :lrsql.spec.admin.params/username
  (s/and string?
         #(>= (count %) u-min-len)
         (partial re-matches #"^[a-zA-Z0-9]*$")))
(s/def :lrsql.spec.admin.params/password
  (s/and string?
         #(>= (count %) p-min-len)
         #(let [pass-set   (set %)
                has-chars? (partial some pass-set)]
            (and
             (has-chars? digit-set)
             (has-chars? upper-set)
             (has-chars? lower-set)
             (has-chars? special-set)))))

;; passhash format may vary by password lib
;; Input passhash is not nilable
(s/def :lrsql.spec.admin.input/passhash string?)
;; Ret passhash (from SQL) is nilable
(s/def :lrsql.spec.admin.ret/passhash (s/nilable string?))
(s/def ::uuid ::xs/uuid)
;; Likewise, OIDC issuer is not nilable for inputs
(s/def :lrsql.spec.admin.input/oidc-issuer string?)
;; But is for ret
(s/def :lrsql.spec.admin.ret/oidc-issuer (s/nilable string?))
;; boolean to indicate whether OIDC is enabled
(s/def :lrsql.spec.admin.input/oidc-enabled? boolean?)
;; Update password params
(s/def ::old-password ::password) ;; old password can be invalid
(s/def ::new-password :lrsql.spec.admin.params/password) ;; new one can't
;; Update password input
(s/def :lrsql.spec.admin.input/new-passhash :lrsql.spec.admin.input/passhash)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inputs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; No validation, used for login
(def admin-params-spec
  (s/keys :req-un [::username
                   ::password]))

;; Validation, used for create/update
(def admin-params-strict-spec
  (s/keys :req-un [:lrsql.spec.admin.params/username
                   :lrsql.spec.admin.params/password]))

(def admin-delete-params-spec
  (s/keys :req-un [::account-id]))

(def update-admin-password-params-spec
  (s/and
   (s/keys :req-un [::old-password
                    ::new-password])
   (fn new-pass-noteq-old-pass
     [{:keys [old-password
              new-password]}]
     (not= old-password new-password))))

(def insert-admin-input-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   :lrsql.spec.admin.input/passhash]))

(def ensure-admin-oidc-input-spec
  (s/keys :req-un [:lrsql.spec.admin.input/oidc-issuer
                   ::username]))

(def insert-admin-oidc-input-spec
  (s/keys :req-un [::c/primary-key
                   ::username
                   :lrsql.spec.admin.input/oidc-issuer]))

(def query-admin-input-spec
  (s/keys :req-un [::username
                   ::password]))

(def query-admin-by-id-input-spec
  (s/keys :req-un [::account-id
                   ::password]))

(def query-validate-admin-input-spec
  (s/keys :req-un [::username
                   ::password]))

(def query-validate-admin-by-id-input-spec
  (s/keys :req-un [::account-id
                   ::password]))

(def admin-id-input-spec
  (s/keys :req-un [::account-id]))

(def delete-admin-input-spec
  (s/merge admin-id-input-spec
           (s/keys :req-un [:lrsql.spec.admin.input/oidc-enabled?])))

(def update-admin-password-input-spec
  (s/keys :req-un [::account-id
                   :lrsql.spec.admin.input/new-passhash]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :lrsql.spec.admin.insert/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/existing-account-error})))

(def insert-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.insert/result]))

(s/def :lrsql.spec.admin.ensure/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/oidc-issuer-mismatch-error})))

(def ensure-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.ensure/result]))

(s/def :lrsql.spec.admin.delete/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error
                    :lrsql.admin/sole-admin-delete-error})))

(def delete-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.delete/result]))

(s/def :lrsql.spec.admin.query/result
  (s/nonconforming
   (s/or :success uuid?
         :failure #{:lrsql.admin/missing-account-error
                    :lrsql.admin/invalid-password-error})))

(def query-admin-ret-spec
  (s/keys :req-un [::account-id
                   :lrsql.spec.admin.ret/passhash
                   ::username]))

(def query-all-admin-accounts-ret-spec
  (s/every (s/keys :req-un [::account-id
                            ::username])))

(def query-account-ret-spec
  (s/keys :req-un [::account-id
                   ::username]))

(def query-validate-admin-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.query/result]))

(s/def :lrsql.spec.admin.update-password/result uuid?)

(def update-admin-password-ret-spec
  (s/keys :req-un [:lrsql.spec.admin.update-password/result]))

(def delete-actor-spec
  (s/keys :req-un [:lrsql.spec.actor/actor-ifi]))
