(ns lrsql.spec.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [xapi-schema.spec :as xs]
            [xapi-schema.spec.regex :as xsr]
            [lrsql.spec.common :as c])
  (:import [java.util Base64 Base64$Encoder]))

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

(s/def ::api-key string?)
(s/def ::secret-key string?)

(s/def ::scope
  #{"statements/write"
    "statements/read/mine"
    "statements/read"
    "state"
    "define"
    "profile"
    "all/read"
    "all"})

(def auth-insert-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key
                   ::scope]))

(def auth-query-spec
  (s/keys :req-un [::c/primary-key
                   ::api-key
                   ::secret-key]))
