(ns lrsql.util.auth
  (:require [clojure.set :as cset]
            [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.nonce  :refer [random-bytes]])
  (:import [java.util Base64 Base64$Decoder]))

(def scope-str-kw-map
  {"all"                  :scope/all
   "all/read"             :scope/all.read
   "profile"              :scope/profile
   "define"               :scope/define
   "state"                :scope/state
   "statements/read"      :scope/statements.read
   "statements/read/mine" :scope/statements.read.mine
   "statements/write"     :scope/statements.write})

(def scope-kw-str-map
  (cset/map-invert scope-str-kw-map))

(defn scope-str->kw
  [scope-str]
  (get scope-str-kw-map scope-str))

(defn scope-kw->str
  [scope-kw]
  (get scope-kw-str-map scope-kw))

(def ^Base64$Decoder decoder (Base64/getDecoder))

(defn header->key-pair
  "Given a Base64 authentication header, return a map with the keys
   `:api-key` and `:secret-key`. The map can then be used as the input to
   `query-authentication`. Return `nil` if the header cannot be decoded."
  [^String auth-header]
  (try (let [auth-part  (subs auth-header 6) ; Remove "Basic " prefix
             decoded    (String. (.decode    ; Base64 -> "username:password"
                                  decoder
                                  auth-part))
             [?api-key
              ?srt-key] (cstr/split decoded #":")]
         {:api-key    (if ?api-key ?api-key "")
          :secret-key (if ?srt-key ?srt-key "")})
       (catch Exception _ nil)))

(defn generate-key-pair
  "Generate a pair of credentials for lrsql: an API key (the \"username\") and
   a secret API key (the \"password\"). Compatiable as `query-authentication`
   input."
  []
  {:api-key    (-> 32 random-bytes bytes->hex)
   :secret-key (-> 32 random-bytes bytes->hex)})

;; Mostly copied from the third LRS:
;; https://github.com/yetanalytics/third/blob/master/src/main/cloud_lrs/impl/auth.cljc
(defn authorize-action
  "Given a pedestal context and an auth identity, authorize or deny."
  [{{:keys [request-method path-info]} :request
    :as _ctx}
   {:keys [_prefix ; useless without tenancy
           scopes
           _auth
           _agent]
    :as _auth-identity}]
  {:result
   (or (contains? scopes :scope/all)
       (and (contains? scopes :scope/all.read)
            (boolean (#{:get :head} request-method)))
       (and (.endsWith ^String path-info "statements")
            (or (and (#{:get :head} request-method)
                     (contains? scopes :scope/statements.read))
                (and (#{:put :post} request-method)
                     (contains? scopes :scope/statements.write))))
       ;; NOTE: There are other scopes - `statements/read/mine`, `state`,
       ;; `define`, and `profile` - that exist and are supported as DB enum
       ;; values, but are unlikely to ever be implemented
       (do
         (let [scopes' (disj scopes
                             :scope/all
                             :scope/all.read
                             :scope/statements.read
                             :scope/statements.write)]
           (when (not-empty scopes')
             (log/errorf "Scopes not currently implemented: %s"
                         (->> scopes' (map scope-kw->str) vec))))
         false))})
