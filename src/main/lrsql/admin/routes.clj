(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [io.pedestal.http :refer [json-body]]
            [io.pedestal.http.body-params :refer [body-params]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.account :as ai]
            [lrsql.admin.interceptors.credentials :as ci]
            [lrsql.admin.interceptors.lrs-management :as lm]
            [lrsql.admin.interceptors.ui :as ui]
            [lrsql.admin.interceptors.jwt :as ji]
            [lrsql.admin.interceptors.status :as si]
            [lrsql.util.interceptor :as util-i]
            [lrsql.util.headers :as h]
            [lrsql.system.openapi :as oa]))

(defn- make-common-interceptors
  [lrs sec-head-opts]
  [i/error-interceptor
   (util-i/handle-json-parse-exn true)
   i/x-forwarded-for-interceptor
   (h/secure-headers sec-head-opts)
   json-body
   (body-params)
   (i/lrs-interceptor lrs)])

(defn admin-account-routes
  [common-interceptors jwt-secret jwt-exp jwt-leeway no-val-opts]
  #{;; Log into an existing account
    (oa/annotate-short
     ["/admin/account/login" :post (conj common-interceptors
                                         ai/validate-params
                                         ai/authenticate-admin
                                         (ai/generate-jwt jwt-secret jwt-exp))
      :route-name :lrsql.admin.account/login]
     {:description "Log into an existing account"
      :requestBody (oa/json-content (oa/owrap {:username {:type :string}
                                               :password {:type :string}}))
      :operationId :login
      :responses {200 (oa/response "Account ID and JWT"
                                   (oa/owrap {:account-id {:type :string}
                                              :json-web-token {:type :string}}))
                  400 oa/error-400
                  401 oa/error-401}})
    ;; Create new account
    (oa/annotate-short
     ["/admin/account/create" :post (conj common-interceptors
                                          ai/validate-params
                                          (ji/validate-jwt
                                           jwt-secret jwt-leeway no-val-opts)
                                          ji/validate-jwt-account
                                          ai/create-admin)
      :route-name :lrsql.admin.account/create]
     {:description "Create new account"
      :requestBody (oa/json-content (oa/owrap {:username {:type "string"}
                                               :password {:type "string"}}))
      :operationId :create-account
      :security [{:bearerAuth []}]
      :responses {200 (oa/response "ID of new account"
                                   (oa/owrap {:account-id {:type "string"}}))
                  400 oa/error-401
                  401 oa/error-401}})
    ;; Update account password
    (oa/annotate-short
     ["/admin/account/password" :put (conj common-interceptors
                                           ai/validate-update-password-params
                                           (ji/validate-jwt jwt-secret jwt-leeway no-val-opts)
                                           ji/validate-jwt-account
                                           ai/update-admin-password)]
     {:description "Update account password"
      :requestBody (oa/json-content(oa/owrap  {:old-password {:type :string}
                                               :new-password {:type :string}}))
      :operationId :update-password
      :security [{:bearerAuth []}]
      :responses {200 (oa/response "ID of updated account"
                                    (oa/owrap {:account-id {:type "string"}}))
                  400 oa/error-400
                  401 oa/error-401}})
    ;; Get all accounts
    (oa/annotate-short
     ["/admin/account" :get (conj common-interceptors
                                  (ji/validate-jwt
                                   jwt-secret jwt-leeway no-val-opts)
                                  ji/validate-jwt-account
                                  ai/get-accounts)
      :route-name :lrsql.admin.account/get]
     {:description "Get all accounts"
      :operationId :get-admin-accounts
      :security [{:bearerAuth []}]
      :responses {200 (oa/response "Array of account objects"
                                   (oa/awrap (oa/owrap {:account-id {:type "string"} 
                                                        :username {:type "string"}})))
                  401 oa/error-401}})
    ;; Get my accounts
    (oa/annotate-short
     ["/admin/me" :get (conj common-interceptors
                             (ji/validate-jwt
                              jwt-secret jwt-leeway no-val-opts)
                             ji/validate-jwt-account
                             ai/me)
      :route-name :lrsql.admin.me/get]
     {:description "Get account of querying account"
      :operationId :get-own-account
      :security [{:bearerAuth []}]
      :responses {200 (oa/response  "Account object referring to own account"
                                    (oa/owrap {:account-id {:type "string"}
                                               :username {:type "string"}}) )
                  401 oa/error-401}})
    ;; Delete account (and associated credentials)
    (oa/annotate-short
     ["/admin/account" :delete (conj common-interceptors
                                       ai/validate-delete-params
                                       (ji/validate-jwt
                                        jwt-secret jwt-leeway no-val-opts)
                                       ji/validate-jwt-account
                                       ai/delete-admin)
        :route-name :lrsql.admin.account/delete]
     {:description "Delete account (and associated credentials)"
      :requestBody (oa/json-content (oa/owrap {:account-id {:type "string"}}))
      :operationId :delete-admin-account
      :security [{:bearerAuth []}]
      :responses {200 (oa/response  "ID of deleted account"
                                    (oa/owrap {:account-id {:type "string"}}))
                  400 oa/error-400
                  401 oa/error-401}})})

(defn admin-cred-routes
  [common-interceptors jwt-secret jwt-leeway no-val-opts]
  #{;; Create new API key pair w/ scope set
    (oa/annotate-short
     ["/admin/creds" :post (conj common-interceptors
                                 (ci/validate-params {:scopes? true})
                                 (ji/validate-jwt
                                  jwt-secret jwt-leeway no-val-opts)
                                 ji/validate-jwt-account
                                 ci/create-api-keys)
      :route-name :lrsql.admin.creds/put]
     {:description "Create new API key pair w/scope set"
      :requestBody (oa/json-content (oa/ref :Scopes))
      :operationId :create-api-keys
      :security [{:bearerAuth []}]
      :responses {400 oa/error-400
                  401 oa/error-401
                  200 (oa/response "Object containing key, secret key, and array of scopes"
                                   (oa/ref :ScopedKeyPair))}})
    ;; Create or update new keys w/ scope set
    (oa/annotate-short
     ["/admin/creds" :put (conj common-interceptors
                                (ci/validate-params {:key-pair? true
                                                     :scopes?   true})
                                (ji/validate-jwt
                                 jwt-secret jwt-leeway no-val-opts)
                                ji/validate-jwt-account
                                ci/update-api-keys)
      :route-name :lrsql.admin.creds/post]
     {:description "Create or update new keys w/scope set"
      :requestBody  (oa/json-content (oa/ref :ScopedKeyPair))
      :operationId :update-api-keys
      :security [{:bearerAuth []}]
      :responses {400 oa/error-400
                  401 oa/error-401
                  200 (oa/response "Key, secret key, and scopes of updated account"
                                   (oa/ref :ScopedKeyPair))}})
    ;; Get current keys + scopes associated w/ account
    (oa/annotate-short
     ["/admin/creds" :get (conj common-interceptors
                                (ji/validate-jwt
                                 jwt-secret jwt-leeway no-val-opts)
                                ji/validate-jwt-account
                                ci/read-api-keys)
      :route-name :lrsql.admin.creds/get]
     {:description "Get current keys + scopes associated w/account"
      :operationId :get-api-keys
      :security [{:bearerAuth []}]
      :responses {200 (oa/response "Array of scoped key pairs"
                                   (oa/awrap (oa/ref :ScopedKeyPair)))
                  401 oa/error-401}})
    ;; Delete API key pair and associated scopes
    (oa/annotate-short
     ["/admin/creds" :delete (conj common-interceptors
                                   (ci/validate-params {:key-pair? true})
                                   (ji/validate-jwt
                                    jwt-secret jwt-leeway no-val-opts)
                                   ji/validate-jwt-account
                                   ci/delete-api-keys)
      :route-name :lrsql.admin.creds/delete]
     {:description  "Delete API key pair and associated scopes"
      :requestBody (oa/json-content (oa/ref :KeyPair))
      :operationId :delete-api-key
      :security [{:bearerAuth []}]
      :responses {200 (oa/response "Empty body" {})
                  400 oa/error-400
                  401 oa/error-401}})})

(defn admin-status-routes
  [common-interceptors jwt-secret jwt-leeway no-val-opts]
  #{;; Return LRS Status information
    ["/admin/status" :get (conj common-interceptors
                                si/validate-params
                                (ji/validate-jwt
                                 jwt-secret jwt-leeway no-val-opts)
                                ji/validate-jwt-account
                                si/get-status)
     :route-name :lrsql.admin.status/get]})

(defn admin-ui-routes
  [common-interceptors {:keys [proxy-path] :as inject-config}]
  #{;; Redirect root to admin UI
    ["/" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/root-redirect]
    ;; Redirect admin w/o slash to admin UI
    ["/admin" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/path-redirect]
    ;; Redirect admin with slash to admin UI
    ["/admin/" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/slash-redirect]
    ["/admin/env" :get (conj common-interceptors
                             (ui/get-env inject-config))
     :route-name :lrsql.admin.ui/get-env]})

(defn admin-lrs-management-routes [common-interceptors jwt-secret jwt-leeway no-val-opts]
  #{["/admin/agents" :delete (conj common-interceptors
                                   lm/validate-delete-actor-params
                                   (ji/validate-jwt jwt-secret jwt-leeway no-val-opts)
                                   ji/validate-jwt-account
                                   lm/delete-actor)
     :route-name :lrsql.lrs-management/delete-actor]
    ["/admin/openapi" :get (conj common-interceptors
                                 (ji/validate-jwt jwt-secret jwt-leeway no-val-opts)
                                 ji/validate-jwt-account
                                 lm/openapi)]})

(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [{:keys [lrs
           exp
           leeway
           secret
           no-val?
           no-val-issuer
           no-val-uname
           no-val-role-key
           no-val-role
           enable-admin-delete-actor
           enable-admin-ui
           enable-admin-status
           proxy-path
           enable-account-routes
           oidc-interceptors
           oidc-ui-interceptors
           head-opts]
    :or   {oidc-interceptors     []
           oidc-ui-interceptors  []
           enable-account-routes true}}
   routes]
  (let [common-interceptors      (make-common-interceptors lrs head-opts)
        common-interceptors-oidc (into common-interceptors oidc-interceptors)
        no-val-opts              {:no-val? no-val?
                                  :no-val-uname no-val-uname
                                  :no-val-issuer no-val-issuer
                                  :no-val-role-key no-val-role-key
                                  :no-val-role no-val-role}]
    (cset/union routes
                (when enable-account-routes
                  (admin-account-routes
                   common-interceptors-oidc secret exp leeway no-val-opts))
                (admin-cred-routes
                 common-interceptors-oidc secret leeway no-val-opts)
                (when enable-admin-ui
                  (admin-ui-routes
                   (into common-interceptors
                         oidc-ui-interceptors)
                   {:enable-admin-status enable-admin-status
                    :no-val? no-val?
                    :proxy-path proxy-path
                    :enable-admin-delete-actor enable-admin-delete-actor}))
                (when enable-admin-status
                  (admin-status-routes
                   common-interceptors-oidc secret leeway no-val-opts))
                (when enable-admin-delete-actor
                  (admin-lrs-management-routes common-interceptors-oidc secret leeway no-val-opts)))))
