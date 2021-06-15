(ns lrsql.input.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.string     :as cstr]
            [lrsql.spec.auth    :as as]
            [lrsql.util         :as u])
  (:import [java.util Base64 Base64$Decoder]))

(def ^Base64$Decoder decoder (Base64/getDecoder))

(s/fdef auth-input
  :args (s/cat :auth-header as/auth-header-spec)
  :ret as/auth-query-spec)

(defn auth-input
  "Given a Base64 authentication header, return a map with the keys
   `:api-key` and `:secret-key`. The map can then be used as the input to
   `query-authentication`."
  [^String auth-header]
  (try (let [auth-part  (subs auth-header 6) ; Remove "Basic " prefix
             decoded    (String. (.decode    ; Base64 -> "username:password"
                                  decoder
                                  auth-part))
             [username
              password] (cstr/split decoded
                                    #":")]
         {:primary-key (u/generate-squuid)
          :api-key     username
          :secret-key  password})
       (catch Exception _
         (throw (ex-info "Cannot decode authentication header!"
                         {:type ::invalid-auth-header
                          :auth-header auth-header})))))

(s/fdef auth-scope-inputs
  :args (s/cat :auth-input as/auth-insert-spec
               :scopes (s/coll-of ::as/scope :min-count 1 :gen-max 5))
  :ret (s/coll-of as/auth-insert-spec :min-count 1))

(defn auth-scope-inputs
  "Given a map returned by `auth-input` and a list of scopes, return a seq
   of maps with a `:scope` key. The seq can then be used as the input to
   `insert-credentials!`"
  [auth-input scopes]
  (map (partial assoc auth-input :scope) scopes))
