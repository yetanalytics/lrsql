(ns lrsql.util.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as cset]
            [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.nonce  :refer [random-bytes]]
            [lrsql.spec.auth :as as])
  (:import [java.util Base64 Base64$Decoder]))

;; NOTE: Additional scopes may be implemented in the future.

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

(s/fdef authorized-action?
  :args (s/cat :ctx           (s/keys :req-un [::request])
               :auth-identity (s/keys :req-un [::scopes]))
  :ret boolean?)

(defn most-permissive-statement-read-scope
  "Given a read action on statements, return the most permissive scope
   contained in `scopes`. Returns `nil` if no scopes are available."
  [{:keys [scopes] :as _auth-identity}]
  (condp #(contains? %2 %1) scopes
    :scope/all :scope/all
    :scope/all.read :scope/all.read
    :scope/statements.read :scope/statements.read
    nil))

(defn most-permissive-statement-write-scope
  "Given a write action on statements, return the most permissive scope
   contained in `scopes`. Returns `nil` if no scopes are available."
  [{:keys [scopes] :as _auth-identity}]
  (condp #(contains? %2 %1) scopes
    :scope/all :scope/all
    :scope/statements.write :scope/statements.write
    nil))

(defn- log-scope-error
  "Log an error if the scope fail happened due to non-existent scopes (as
   opposed to authorization denial)."
  [scopes]
  (log/error (str "Scopes: " scopes))
  (when-some [err (s/explain-data ::scopes scopes)]
    (log/errorf "Scope set included unimplemented or non-existent scopes.\nErrors:\n%s"
                (with-out-str (s/explain-out err)))))

(defn authorized-action?
  "Given a pedestal context and an auth identity, return `true` and authorize
   if `scopes` are valid for the request's type (read vs. write) and resource
   path, return `false` and deny otherwise."
  [{{:keys [request-method path-info]} :request
    :as _ctx}
   {:keys [_prefix ; useless without tenancy
           scopes
           _auth
           _agent]
    :as auth-identity}]
  (cond
    ;; /statements path
    (some-> path-info not-empty (cstr/ends-with? "statements"))
    (cond
      (#{:get :head} request-method)
      (some? (most-permissive-statement-read-scope auth-identity))
      (#{:put :post} request-method) ; No statement delete implemented
      (some? (most-permissive-statement-write-scope auth-identity))
      :else
      (do (log-scope-error scopes)
          false))
    ;; all other paths (e.g. /about, /activities)
    :else
    (cond
      (#{:get :head} request-method)
      (or (contains? scopes :scope/all)
          (contains? scopes :scope/all.read))
      (#{:put :post :delete} request-method)
      (contains? scopes :scope/all)
      :else
      (do (log-scope-error scopes)
          false))))
