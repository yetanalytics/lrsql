(ns lrsql.util.auth
  (:require [clojure.set :as cset]))

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
       (and (contains? scopes :scope.all/read)
            (#{:get :head} request-method))
       (and (.endsWith ^String path-info "statements")
            (or (and (#{:get :head} request-method)
                     (contains? scopes :scope.statements/read))
                (and (#{:put :post} request-method)
                     (contains? scopes :scope.statements/write))))
       ;; TODO: this could be implemented much faster
       ;; TODO: implement scopes: statements/read/mine, state, define, profile
       false)})
