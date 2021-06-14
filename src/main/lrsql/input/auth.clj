(ns lrsql.input.auth
  (:require [clojure.string :as cstr])
  (:import [java.util Base64 Base64$Decoder]))

(def ^Base64$Decoder decoder (Base64/getDecoder))

(defn auth-input
  [^String auth-header]
  (try (let [auth-part  (subs auth-header 6) ; Remove "Basic " prefix
             decoded    (String. (.decode    ; Base64 -> "username:password"
                                  decoder
                                  auth-part))
             [username
              password] (cstr/split decoded
                                    #":")]
         {:api-key    username
          :secret-key password})
       (catch Exception _
         (throw (ex-info "Cannot decode authentication header!"
                         {:type ::invalid-auth-header
                          :auth-header auth-header})))))

(defn add-scope-input
  [auth-input scopes]
  (map (partial assoc auth-input :scope) scopes))
