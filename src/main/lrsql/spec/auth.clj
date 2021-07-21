(ns lrsql.spec.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.interface.protocol :as ip]
            [lrsql.spec.common :as c]
            [lrsql.spec.admin :as ads])
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
              byts (.encode ^Base64$Encoder (Base64/getEncoder) (.getBytes up))]
          (str "Basic " (String. byts))))
      (sgen/tuple (sgen/fmap xs/into-str
                             (sgen/vector (sgen/char-alpha) 3 16))
                  (sgen/fmap xs/into-str
                             (sgen/vector (sgen/char-alpha) 3 16))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn credential-interface?
  [inf]
  (satisfies? ip/Credential inf))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Axioms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::api-key string?)
(s/def ::secret-key string?)

(s/def ::account-id ::c/primary-key)

(s/def ::scope
  #{"statements/write"
    "statements/read/mine"
    "statements/read"
    "state"
    "define"
    "profile"
    "all/read"
    "all"})

(s/def ::scopes
  (s/coll-of ::scope :min-count 1 :gen-max 5 :distinct true))

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
                   ::scopes]))

(def key-pair-args-spec
  (s/alt :map  key-pair-spec
         :args (s/cat :api-key    ::api-key
                      :secret-key ::secret-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Insert
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def insert-cred-input-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key
                   ::ads/account-id]))

(def insert-cred-scope-input-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key
                   ::scope]))

(def insert-cred-scopes-input-spec
  (s/coll-of insert-cred-scope-input-spec :min-count 1 :gen-max 5))

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
  (s/coll-of delete-cred-scope-input-spec :min-count 1 :gen-max 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def query-creds-input-spec
  (s/keys :req-un [::ads/account-id]))

(def query-cred-scopes-input-spec
  (s/keys :req-un [::api-key
                   ::secret-key]))
