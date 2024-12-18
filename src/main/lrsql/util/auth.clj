(ns lrsql.util.auth
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as cset]
            [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.nonce  :refer [random-bytes]]
            [lrsql.spec.auth :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scopes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE: Additional scopes may be implemented in the future.

(def scope-str-kw-map
  {"all"                  :scope/all
   "all/read"             :scope/all.read
   "state"                :scope/state
   "activities_profile"   :scope/activities_profile
   "agents_profile"       :scope/agents_profile
   "statements/read"      :scope/statements.read
   "statements/write"     :scope/statements.write
   "statements/read/mine" :scope/statements.read.mine})

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

(def read-scope-hierarchy
  "The hierarchy of read scopes for GET and HEAD operations."
  (-> (make-hierarchy)
      (derive :scope/statements.read.mine :scope/statements.read)
      (derive :scope/statements.read :scope/all.read)
      (derive :scope/state :scope/all.read)
      (derive :scope/activities_profile :scope/all.read)
      (derive :scope/agents_profile :scope/all.read)
      (derive :scope/all.read :scope/all)))

(def write-scope-hierarchy
  "The hierarchy of write scopes for PUT, POST, and DELETE operations."
  (-> (make-hierarchy)
      (derive :scope/statements.write :scope/all)
      (derive :scope/state :scope/all)
      (derive :scope/activities_profile :scope/all)
      (derive :scope/agents_profile :scope/all)))

(defn- get-scopes
  "Return a set of valid scopes for the operation associated with `scope`,
   with `scope` being the lowest-ranking scope in `hierarchy`."
  [hierarchy scope]
  (cset/union #{scope} (ancestors hierarchy scope)))

(defn- get-most-permissive-scope
  "Return the most permissive scope in `scopes` for `hierarchy`, or
   `nil` when `scopes` is empty."
  [hierarchy scopes]
  (let [count-ancestors (fn [scope]
                          (count (ancestors hierarchy scope)))]
    (when (not-empty scopes)
      (apply min-key count-ancestors scopes))))

(def statement-read-scopes
  (get-scopes read-scope-hierarchy :scope/statements.read.mine))

(def statement-write-scopes
  (get-scopes write-scope-hierarchy :scope/statements.write))

(def state-read-scopes
  (get-scopes read-scope-hierarchy :scope/state))

(def state-write-scopes
  (get-scopes write-scope-hierarchy :scope/state))

(def activities-profile-read-scopes
  (get-scopes read-scope-hierarchy :scope/activities_profile))

(def activities-profile-write-scopes
  (get-scopes write-scope-hierarchy :scope/activities_profile))

(def agents-profile-read-scopes
  (get-scopes read-scope-hierarchy :scope/agents_profile))

(def agents-profile-write-scopes
  (get-scopes write-scope-hierarchy :scope/agents_profile))

(def read-scopes
  (get-scopes read-scope-hierarchy :scope/all.read))

(def write-scopes
  (get-scopes write-scope-hierarchy :scope/all))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Key Pairs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
               ^String decoded     (u/base64encoded-str->str auth-part)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; These specs are also used in `lrsql.util.oidc`
;; We define them here since they are request-specific and we want to avoid
;; clashes with the `lrsql.spec.auth` namespace.

(s/def ::request-method #{:get :head :put :post :delete})
(s/def ::path-info string?)
(s/def ::request (s/keys :req-un [::request-method ::path-info]))

(s/def ::scope as/keyword-scopes)
(s/def ::scopes (s/coll-of ::scope :kind set?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Authorization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Inspired by the third LRS:
;; https://github.com/yetanalytics/third/blob/master/src/main/cloud_lrs/impl/auth.cljc

(defn- log-scope-error
  "Log an error if the scope fail happened due to non-existent scopes (as
   opposed to authorization denial)."
  [scopes]
  (when-some [err (s/explain-data ::scopes scopes)]
    (log/errorf "Scope set included unimplemented or non-existent scopes.\nErrors:\n%s"
                (with-out-str (s/explain-out err)))))

(defn- authorized-scopes?*
  "Return `true` if any one of the `scopes` is in `permitted-scope-set`."
  [permitted-scope-set scopes]
  (or (boolean
       (some (fn [scope] (contains? permitted-scope-set scope))
             scopes))
      (do (log-scope-error scopes)
          false)))

(defn- authorized-scopes?
  "Return `true` if any one of the `scopes` is authorized with the given
   `read-scopes` or `write-scopes`. Which scope set is chosen depends
   on `request-method` (and `allow-delete?`)."
  [read-scopes write-scopes request-method scopes
   & {:keys [allow-delete?]
      :or {allow-delete? true}}]
  (cond
    (#{:get :head} request-method)
    (authorized-scopes?* read-scopes scopes)
    (#{:put :post} request-method)
    (authorized-scopes?* write-scopes scopes)
    (and allow-delete?
         (#{:delete} request-method))
    (authorized-scopes?* write-scopes scopes)
    :else
    (do (log-scope-error scopes)
        false)))

(s/fdef authorized-action?
  :args (s/cat :ctx           (s/keys :req-un [::request])
               :auth-identity (s/keys :req-un [::scopes]))
  :ret boolean?)

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
    :as _auth-identity}]
  (let [path (or path-info "")]
    (condp (fn [suffix path] (cstr/ends-with? path suffix)) path
      "statements"
      (authorized-scopes? statement-read-scopes
                          statement-write-scopes
                          request-method
                          scopes
                          :allow-delete? false)
      "activities/state"
      (authorized-scopes? state-read-scopes
                          state-write-scopes
                          request-method
                          scopes)
      "activities/profile"
      (authorized-scopes? activities-profile-read-scopes
                          activities-profile-write-scopes
                          request-method
                          scopes)
      "agents/profile"
      (authorized-scopes? agents-profile-read-scopes
                          agents-profile-write-scopes
                          request-method
                          scopes)
      ;; all other paths (e.g. /about, /activities)
      (authorized-scopes? read-scopes
                          write-scopes
                          request-method
                          scopes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; statement/read/mine special handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef statement-read-mine-authorization?
  :args (s/cat :auth-identity (s/keys :req-un [::scopes]))
  :ret boolean?)

(defn statement-read-mine-authorization?
  "Return `true` if the most permissive scope for Statement reading is
   `:scope/statements.read.mine`, false if there are more permissive scopes
   (which would override that scope) or if it's not present."
  [{:keys [scopes] :as _auth-identity}]
  (->> scopes
       (filter #(contains? statement-read-scopes %))
       (get-most-permissive-scope read-scope-hierarchy)
       (= :scope/statements.read.mine)))
