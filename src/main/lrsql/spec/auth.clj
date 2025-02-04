(ns lrsql.spec.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :as c]
            [lrsql.spec.admin :as ads]
            [lrsql.spec.authority :as ats]
            [lrsql.util :as u])
  (:import [java.util Base64 Base64$Encoder]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CURL
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def auth-header-spec
  (s/with-gen
    (s/and string?
           (s/conformer #(subs % 6))
           (partial re-matches xsr/Base64RegEx))
    #(sgen/fmap
      (fn [[username password]]
        (let [up   (str username ":" password)
              byts (.encode ^Base64$Encoder (Base64/getEncoder)
                            ^"[B" (u/str->bytes up))]
          (str "Basic " (u/bytes->str byts))))
      (sgen/tuple (sgen/fmap xs/into-str
                             (sgen/vector (sgen/char-alpha) 3 16))
                  (sgen/fmap xs/into-str
                             (sgen/vector (sgen/char-alpha) 3 16))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn credential-backend?
  [bk]
  (satisfies? bp/CredentialBackend bk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def string-scopes
  #{"all"
    "all/read"
    "state"
    "activities_profile"
    "agents_profile"
    "statements/read"
    "statements/read/mine"
    "statements/write"})

(def keyword-scopes
  #{:scope/all
    :scope/all.read
    :scope/state
    :scope/activities_profile
    :scope/agents_profile
    :scope/statements.read
    :scope/statements.read.mine
    :scope/statements.write})

(s/def ::api-key string?)
(s/def ::secret-key string?)

(s/def ::label (s/nilable string?))

(s/def ::scope string-scopes)

(s/def ::ids
  (s/keys :req-un [::ats/cred-id ::ats/account-id]))

(s/def ::scopes
  (s/coll-of ::scope :gen-max 5 :distinct true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def key-pair-spec
  (s/keys :req-un [::api-key ::secret-key]))

(def scopes-spec
  (s/keys :req-un [::scopes]))

(def scoped-key-pair-spec
  (s/keys :req-un [::api-key
                   ::secret-key
                   ::label
                   ::scopes]))

(def key-pair-args-spec
  (s/alt :map  key-pair-spec
         :args (s/cat :api-key    ::api-key
                      :secret-key ::secret-key)))

(def key-pair-authority-args-spec
  (s/alt :map (s/cat :authority-fn ::ats/authority-fn
                     :authority-url ::ats/authority-url
                     :key-pair key-pair-spec)
         :args (s/cat :authority-fn ::ats/authority-fn
                      :authority-url ::ats/authority-url
                      :api-key ::api-key
                      :secret-key ::secret-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insert
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def insert-cred-input-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key
                   ::label
                   ::ads/account-id]))

(def insert-cred-scope-input-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key
                   ::scope]))

(def insert-cred-scopes-input-spec
  (s/coll-of insert-cred-scope-input-spec :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def update-cred-label-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key
                   ::label]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def delete-cred-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key
                   ::ads/account-id]))

(def delete-cred-scope-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key
                   ::scope]))

(def delete-cred-scopes-input-spec
  (s/coll-of delete-cred-scope-input-spec :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-creds-input-spec
  (s/keys :req-un [::ads/account-id]))

(def query-cred-scopes*-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key]))

(def query-cred-scopes-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key
                   ::ats/authority-url
                   ::ats/authority-fn]))
