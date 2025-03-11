(ns lrsql.admin.routes
  (:require [clojure.set :as cset]
            [io.pedestal.http :refer [json-body]]
            [io.pedestal.http.body-params :refer [body-params
                                                  default-parser-map]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.interceptors.account :as ai]
            [lrsql.admin.interceptors.credentials :as ci]
            [lrsql.admin.interceptors.lrs-management :as lm]
            [lrsql.admin.interceptors.openapi :as openapi]
            [lrsql.admin.interceptors.ui :as ui]
            [lrsql.admin.interceptors.jwt :as ji]
            [lrsql.admin.interceptors.status :as si]
            [lrsql.admin.interceptors.reaction :as ri]
            [lrsql.util.interceptor :as util-i]
            [lrsql.util.headers :as h]
            [com.yetanalytics.gen-openapi.core :as gc]
            [com.yetanalytics.gen-openapi.generate :as g]
            [com.yetanalytics.gen-openapi.generate.schema :as gs]))

(defn- make-common-interceptors
  [lrs sec-head-opts]
  [i/error-interceptor
   (util-i/handle-json-parse-exn true)
   i/x-forwarded-for-interceptor
   (h/secure-headers sec-head-opts)
   json-body
   (body-params
    ;; By default the JSON parser will attempt to parse strings with `/` as
    ;; qualified keywords. We prevent this so (name x) is always the string.
    (default-parser-map
     :json-options {:key-fn #(keyword nil %)}))
   (i/lrs-interceptor lrs)])

(defn admin-account-routes
  [common-interceptors jwt-secret jwt-exp jwt-ref jwt-leeway {:keys [no-val?] :as no-val-opts}]
  #{;; Log into an existing account
    (gc/annotate
     ["/admin/account/login" :post (conj common-interceptors
                                         (ai/validate-params
                                          :strict? false)
                                         ai/authenticate-admin
                                         (ai/generate-jwt
                                          jwt-secret jwt-exp jwt-ref jwt-leeway))
      :route-name :lrsql.admin.account/login]
     {:description "Log into an existing account"
      :requestBody (g/request (gs/o {:username :t#string
                                     :password :t#string}))
      :operationId :login
      :responses {200 (g/response "Account ID and JWT"
                                  (gs/o {:account-id :t#string
                                         :json-web-token :t#string}))
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})
    ;; Log out of current account
    (gc/annotate
     ["/admin/account/logout" :post (conj common-interceptors
                                          (ji/validate-jwt
                                           jwt-secret jwt-leeway no-val-opts)
                                          ji/validate-jwt-account
                                          (ai/block-admin-jwt
                                           jwt-exp jwt-leeway no-val?))
      :route-name :lrsql.admin.account/logout]
     {:description "Log out of this account"
      :operationId :logout
      :responses {200 (g/response "Account ID"
                                  (gs/o {:account-id :t#string}))
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})
    ;; Renew current account JWT to maintain login
    (gc/annotate
     ["/admin/account/renew" :get (conj common-interceptors
                                        (ji/validate-jwt
                                         jwt-secret jwt-leeway no-val-opts)
                                        ji/validate-jwt-account
                                        (ai/renew-admin-jwt jwt-secret jwt-exp))
      :route-name :lrsql.admin.account/renew]
     {:description "Renew current account login"
      :operationId :renew
      :responses {200 (g/response "Account ID and JWT")
                  401 (g/rref :error-401)}})
    ;; Create new account
    (gc/annotate
     ["/admin/account/create" :post (conj common-interceptors
                                          (ai/validate-params
                                          :strict? true)
                                          (ji/validate-jwt
                                           jwt-secret jwt-leeway no-val-opts)
                                          ji/validate-jwt-account
                                          ai/create-admin)
      :route-name :lrsql.admin.account/create]
     {:description "Create new account"
      :requestBody (g/request (gs/o {:username :t#string
                                     :password :t#string}))
      :operationId :create-account
      :security [{:bearerAuth []}]
      :responses {200 (g/response "ID of new account"
                                   (gs/o {:account-id :t#string}))
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})
    ;; Update account password
    (gc/annotate
     ["/admin/account/password" :put (conj common-interceptors
                                           ai/validate-update-password-params
                                           (ji/validate-jwt jwt-secret jwt-leeway no-val-opts)
                                           ji/validate-jwt-account
                                           ai/update-admin-password)]
     {:description "Update account password"
      :requestBody (g/request (gs/o {:old-password :t#string
                                     :new-password :t#string}))
      :operationId :update-password
      :security [{:bearerAuth []}]
      :responses {200 (g/response "ID of updated account"
                                  (gs/o {:account-id :t#string}))
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})
    ;; Get all accounts
    (gc/annotate
     ["/admin/account" :get (conj common-interceptors
                                  (ji/validate-jwt
                                   jwt-secret jwt-leeway no-val-opts)
                                  ji/validate-jwt-account
                                  ai/get-accounts)
      :route-name :lrsql.admin.account/get]
     {:description "Get all accounts"
      :operationId :get-admin-accounts
      :security [{:bearerAuth []}]
      :responses {200 (g/response "Array of account objects"
                                  (gs/a (gs/o {:account-id :t#string 
                                               :username :t#string})))
                  401 (g/rref :error-401)}})
    ;; Get my account
    (gc/annotate
     ["/admin/me" :get (conj common-interceptors
                             (ji/validate-jwt
                              jwt-secret jwt-leeway no-val-opts)
                             ji/validate-jwt-account
                             ai/me)
      :route-name :lrsql.admin.me/get]
     {:description "Get account of querying account"
      :operationId :get-own-account
      :security [{:bearerAuth []}]
      :responses {200 (g/response  "Account object referring to own account"
                                   (gs/o {:account-id :t#string
                                          :username :t#string}) )
                  401 (g/rref :error-401)}})
    ;; Check that I am logged in
    (gc/annotate
     ["/admin/verify" :get (conj common-interceptors
                                 (ji/validate-jwt
                                  jwt-secret jwt-leeway no-val-opts)
                                 ji/validate-jwt-account
                                 ai/no-content)
      :route-name :lrsql.admin.verify/get]
     {:description "Verify that querying account is logged in"
      :operationId :verify-own-account
      :security [{:bearerAuth []}]
      :responses {204 (g/response "No content body")
                  401 (g/rref :error-401)}})
    ;; Delete account (and associated credentials)
    (gc/annotate
     ["/admin/account" :delete (conj common-interceptors
                                       ai/validate-delete-params
                                       (ji/validate-jwt
                                        jwt-secret jwt-leeway no-val-opts)
                                       ji/validate-jwt-account
                                       ai/delete-admin)
        :route-name :lrsql.admin.account/delete]
     {:description "Delete account (and associated credentials)"
      :requestBody (g/request (gs/o {:account-id :t#string}))
      :operationId :delete-admin-account
      :security [{:bearerAuth []}]
      :responses {200 (g/response  "ID of deleted account"
                                   (gs/o {:account-id :t#string}))
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})})

(defn admin-cred-routes
  [common-interceptors jwt-secret jwt-leeway no-val-opts]
  #{;; Create new API key pair w/ scope set
    (gc/annotate
     ["/admin/creds" :post (conj common-interceptors
                                 (ci/validate-params {:scopes? true})
                                 (ji/validate-jwt
                                  jwt-secret jwt-leeway no-val-opts)
                                 ji/validate-jwt-account
                                 ci/create-api-keys)
      :route-name :lrsql.admin.creds/put]
     {:description "Create new API key pair w/scope set"
      :requestBody (g/request :r#Scopes)
      :operationId :create-api-keys
      :security [{:bearerAuth []}]
      :responses {400 (g/rref :error-400)
                  401 (g/rref :error-401)
                  200 (g/response "Object containing key, secret key, and array of scopes"
                                  :r#ScopedKeyPair)}})
    ;; Create or update new keys w/ scope set
    (gc/annotate
     ["/admin/creds" :put (conj common-interceptors
                                (ci/validate-params {:key-pair? true
                                                     :scopes?   true})
                                (ji/validate-jwt
                                 jwt-secret jwt-leeway no-val-opts)
                                ji/validate-jwt-account
                                ci/update-api-keys)
      :route-name :lrsql.admin.creds/post]
     {:description "Create or update new keys w/scope set"
      :requestBody  (g/request :r#ScopedKeyPair)
      :operationId :update-api-keys
      :security [{:bearerAuth []}]
      :responses {400 (g/rref :error-400)
                  401 (g/rref :error-401)
                  200 (g/response "Key, secret key, and scopes of updated account"
                                  :r#ScopedKeyPair)}})
    ;; Get current keys + scopes associated w/ account
    (gc/annotate
     ["/admin/creds" :get (conj common-interceptors
                                (ji/validate-jwt
                                 jwt-secret jwt-leeway no-val-opts)
                                ji/validate-jwt-account
                                ci/read-api-keys)
      :route-name :lrsql.admin.creds/get]
     {:description "Get current keys + scopes associated w/account"
      :operationId :get-api-keys
      :security [{:bearerAuth []}]
      :responses {200 (g/response "Array of scoped key pairs"
                                   (gs/a :r#ScopedKeyPair))
                  401 (g/rref :error-401)}})
    ;; Delete API key pair and associated scopes
    (gc/annotate
     ["/admin/creds" :delete (conj common-interceptors
                                   (ci/validate-params {:key-pair? true})
                                   (ji/validate-jwt
                                    jwt-secret jwt-leeway no-val-opts)
                                   ji/validate-jwt-account
                                   ci/delete-api-keys)
      :route-name :lrsql.admin.creds/delete]
     {:description  "Delete API key pair and associated scopes"
      :requestBody (g/request :r#KeyPair)
      :operationId :delete-api-key
      :security [{:bearerAuth []}]
      :responses {200 (g/response "Empty body" {})
                  400 (g/rref :error-400)
                  401 (g/rref :error-401)}})})

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
  #{["/admin/env" :get (conj common-interceptors
                             (ui/get-env inject-config))
     :route-name :lrsql.admin.ui/get-env]
    ;; SPA routes
    ["/admin/ui" :get (ui/get-spa proxy-path)
     :route-name :lrsql.admin.ui/main-path]
    ["/admin/ui/" :get (ui/get-spa proxy-path)
     :route-name :lrsql.admin.ui/slash-path]
    ["/admin/ui/*path" :get (ui/get-spa proxy-path)
     :route-name :lrsql.admin.ui/sub-paths]
    ;; SPA redirects
    ["/" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/root-redirect]
    ["/admin" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/slash-redirect]
    ["/admin/" :get (ui/admin-ui-redirect proxy-path)
     :route-name :lrsql.admin.ui/slash-redirect-2]})

(defn admin-reaction-routes
  [common-interceptors jwt-secret jwt-leeway no-val-opts]
  #{;; Create a reaction
    ["/admin/reaction" :post (conj common-interceptors
                                   ri/validate-create-reaction-params
                                   (ji/validate-jwt
                                    jwt-secret jwt-leeway no-val-opts)
                                   ji/validate-jwt-account
                                   ri/create-reaction)
     :route-name :lrsql.admin.reaction/post]
    ;; Get all reactions
    ["/admin/reaction" :get (conj common-interceptors
                                  (ji/validate-jwt
                                   jwt-secret jwt-leeway no-val-opts)
                                  ji/validate-jwt-account
                                  ri/get-all-reactions)
     :route-name :lrsql.admin.reaction/get]
    ;; Update a reaction
    ["/admin/reaction" :put (conj common-interceptors
                                  ri/validate-update-reaction-params
                                  (ji/validate-jwt
                                   jwt-secret jwt-leeway no-val-opts)
                                  ji/validate-jwt-account
                                  ri/update-reaction)
     :route-name :lrsql.admin.reaction/put]
    ;; Delete a reaction
    ["/admin/reaction" :delete (conj common-interceptors
                                     ri/validate-delete-reaction-params
                                     (ji/validate-jwt
                                      jwt-secret jwt-leeway no-val-opts)
                                     ji/validate-jwt-account
                                     ri/delete-reaction)
     :route-name :lrsql.admin.reaction/delete]})

(defn admin-lrs-management-routes
  [common-interceptors-no-auth
   common-interceptors jwt-secret jwt-exp jwt-leeway no-val-opts]
  #{["/admin/agents" :delete (conj common-interceptors
                                   lm/validate-delete-actor-params
                                   (ji/validate-jwt jwt-secret jwt-leeway no-val-opts)
                                   ji/validate-jwt-account
                                   lm/delete-actor)
     :route-name :lrsql.lrs-management/delete-actor]
    ["/admin/csv/auth" :get (conj common-interceptors
                                  (ji/validate-jwt
                                   jwt-secret jwt-leeway no-val-opts)
                                  ji/validate-jwt-account
                                  (lm/generate-one-time-jwt jwt-secret jwt-exp))
     :route-name :lrsql.lrs-management/download-csv-auth]
    ["/admin/csv" :get (conj common-interceptors-no-auth
                             lm/validate-property-paths
                             lm/validate-query-params
                             (ji/validate-one-time-jwt jwt-secret jwt-leeway)
                             lm/download-statement-csv)
     :route-name :lrsql.lrs-management/download-csv]})

(defn add-admin-routes
  "Given a set of routes `routes` for a default LRS implementation,
   add additional routes specific to creating and updating admin
   accounts."
  [{:keys [lrs
           exp
           ref
           leeway
           secret
           no-val?
           no-val-issuer
           no-val-uname
           no-val-role-key
           no-val-role
           no-val-logout-url
           refresh-interval
           interaction-window
           enable-admin-delete-actor
           enable-admin-ui
           enable-admin-status
           admin-language-code
           stmt-get-max
           proxy-path
           enable-account-routes
           enable-reaction-routes
           oidc-interceptors
           oidc-ui-interceptors
           head-opts]
    :or   {oidc-interceptors     []
           oidc-ui-interceptors  []
           enable-account-routes true}}
   routes]
  (let [common-interceptors      (make-common-interceptors lrs head-opts)
        common-interceptors-oidc (into common-interceptors oidc-interceptors)
        no-val-opts              {:no-val?         no-val?
                                  :no-val-uname    no-val-uname
                                  :no-val-issuer   no-val-issuer
                                  :no-val-role-key no-val-role-key
                                  :no-val-role     no-val-role}]
    (cset/union routes
                (when enable-account-routes
                  (admin-account-routes
                   common-interceptors-oidc secret exp ref leeway no-val-opts))
                (admin-cred-routes
                 common-interceptors-oidc secret leeway no-val-opts)
                (when enable-admin-ui
                  (admin-ui-routes
                   (into common-interceptors
                         oidc-ui-interceptors)
                   {:jwt-refresh-interval      refresh-interval
                    :jwt-interaction-window    interaction-window
                    :enable-admin-status       enable-admin-status
                    :enable-reactions          enable-reaction-routes
                    :no-val?                   no-val?
                    :no-val-logout-url         no-val-logout-url
                    :proxy-path                proxy-path
                    :stmt-get-max              stmt-get-max
                    :enable-admin-delete-actor enable-admin-delete-actor
                    :admin-language-code       admin-language-code}))
                (when enable-admin-status
                  (admin-status-routes
                   common-interceptors-oidc secret leeway no-val-opts))
                (when enable-reaction-routes
                  (admin-reaction-routes
                   common-interceptors-oidc secret leeway no-val-opts))
                (when enable-admin-delete-actor
                  (admin-lrs-management-routes
                   common-interceptors common-interceptors-oidc secret exp leeway no-val-opts)))))

(defn add-openapi-route [{:keys [lrs head-opts version]} routes]
  (let [common-interceptors (make-common-interceptors lrs head-opts)]
    (conj routes ["/admin/openapi" :get (conj common-interceptors
                                              (openapi/openapi routes version))])))
