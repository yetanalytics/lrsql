(ns lrsql.util.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as cset]
            [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.nonce  :refer [random-bytes]]
            [lrsql.spec.auth :as as])
  (:import [java.util Base64 Base64$Decoder]))

(def scope-str-kw-map
  {"all"                  :scope/all
   "all/read"             :scope/all.read
   "statements/read"      :scope/statements.read
   "statements/write"     :scope/statements.write})

(def scope-kw-str-map
  (cset/map-invert scope-str-kw-map))

(defn scope-str->kw
  "Convert a scope string into the appropriate internal keyword, e.g. `all`
   becomes `:scope/all`."
  [scope-str]
  (get scope-str-kw-map scope-str))

(defn scope-kw->str
  "Convert a scope keyword into the appropriate string, e.g. `:scope/all`
   becomes `all`."
  [scope-kw]
  (get scope-kw-str-map scope-kw))

(def ^Base64$Decoder decoder
  "The default Base64 decoder."
  (Base64/getDecoder))

(s/fdef header->key-pair
  :args (s/cat :auth-header (s/nilable string?))
  :ret (s/nilable as/key-pair-spec))

(defn header->key-pair
  "Given a header of the form `Basic [Base64 string]`, return a map with keys
   `:api-key` and `:secret-key`. The map can then be used as the input to
   `query-authentication`. Return `nil` if the header is `nil` or cannot
   be decoded."
  [auth-header]
  (when auth-header
    (try (let [^String auth-part   (second (re-matches #"Basic\s+(.*)"
                                                       auth-header))
               ^String decoded     (String. (.decode decoder
                                                     auth-part))
               [?api-key ?srt-key] (cstr/split decoded #":")]
           {:api-key    (if ?api-key ?api-key "")
            :secret-key (if ?srt-key ?srt-key "")})
         (catch Exception _ nil))))

(s/fdef generate-key-pair
  :args (s/cat)
  :ret as/key-pair-spec)

(defn generate-key-pair
  "Generate a pair of credentials for lrsql: an API key (the \"username\") and
   a secret API key (the \"password\"). Compatiable as `query-authentication`
   input."
  []
  {:api-key    (-> 32 random-bytes bytes->hex)
   :secret-key (-> 32 random-bytes bytes->hex)})

;; NOTE: There are other scopes - `statements/read/mine`, `state`,
;; `define`, and `profile` - that exist and are supported as DB enum
;; values, but are unlikely to ever be implemented

;; Mostly copied from the third LRS:
;; https://github.com/yetanalytics/third/blob/master/src/main/cloud_lrs/impl/auth.cljc

(s/def ::request-method #{:get :head :put :post :delete})
(s/def ::path-info string?)
(s/def ::request (s/keys :req-un [::request-method ::path-info]))

;; Need separate spec since the one in spec.auth is for the string versions.
(s/def ::scope #{:scope/all
                 :scope/all.read
                 :scope/statements.read
                 :scope/statements.write})
(s/def ::scopes (s/coll-of ::scope :kind set?))

(s/def ::result boolean?)

(s/fdef authorize-action
  :args (s/cat :ctx           (s/keys :req-un [::request])
               :auth-identity (s/keys :req-un [::scopes]))
  :ret (s/keys :req-un [::result]))

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
   (boolean
    (or
     ;; `all` scope: everything is permitted
     (contains? scopes :scope/all)
     ;; `all/read` scope: only GET/HEAD requests permitted
     (and (contains? scopes :scope/all.read)
          (#{:get :head} request-method))
     ;; `statements/read` and `statements/write`: path needs to be for stmts
     (and (not-empty path-info)
          (cstr/ends-with? path-info "statements")
          (or (and (#{:get :head} request-method)
                   (contains? scopes :scope/statements.read))
              (and (#{:put :post} request-method)
                 (contains? scopes :scope/statements.write))))
     ;; Invalid scopes
     (do
       (let [scopes' (disj scopes
                           :scope/all
                           :scope/all.read
                           :scope/statements.read
                           :scope/statements.write)]
         (when (not-empty scopes')
           (log/errorf "Scopes not currently implemented: %s"
                       (->> scopes' (map scope-kw->str) vec))))
       false)))})
