(ns lrsql.admin.interceptors.account
  (:require [clojure.spec.alpha :as s]
            [java-time.api :as jt]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [lrsql.admin.protocol :as adp]
            [lrsql.spec.admin :as ads]
            [lrsql.util.admin :as admin-u]
            [lrsql.admin.interceptors.jwt :as jwt]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn validate-params
  "Validate that the JSON params contain the params `username` and `password`
   for login and delete. If `:strict?` kwarg is true (default) then the username
   and password will need to pass additional requirements."
  [& {:keys [strict?]
      :or {strict? true}}]
  (interceptor
   {:name ::validate-params
    :enter
    (fn validate-params [ctx]
      (let [params (get-in ctx [:request :json-params])]
        (if-some [err (s/explain-data
                       (if strict?
                         ads/admin-params-strict-spec
                         ads/admin-params-spec)
                       params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (let [acc-info (select-keys params [:username :password])]
            (-> ctx
                (assoc ::data acc-info)
                (assoc-in [:request :session ::data] acc-info))))))}))

(def validate-update-password-params
  "Validate that the JSON params contain the params `old-password`
   and `new-password` for password update. Also validates that `old-password`
   `new-password` do not match."
  (interceptor
   {:name ::validate-update-password-params
    :enter
    (fn validate-params [ctx]
      (let [params (get-in ctx [:request :json-params])]
        (if-some [err (s/explain-data
                       ads/update-admin-password-params-spec params)]
          ;; Invalid parameters - Bad Request
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "Invalid parameters:\n%s"
                                          (-> err s/explain-out with-out-str))}})
          ;; Valid params - continue
          (let [update-info (select-keys params
                                         [:old-password :new-password])]
            (-> ctx
                (assoc ::data update-info)
                (assoc-in [:request :session ::data] update-info))))))}))

(def validate-delete-params
  "Validate that the JSON params contain `account-id` for delete."
  (interceptor
   {:name ::validate-delete-params
    :enter
    (fn validate-params [{{{:keys [account-id] :as params} :json-params}
                          :request :as ctx}]
      (if (not (s/valid? ::ads/uuid account-id))
        ;; Not a valid UUID - Bad Request
        (assoc (chain/terminate ctx)
               :response
               {:status 400
                :body   {:error "Invalid parameters: account-id must be a uuid."}})
        ;; Valid UUID
        (let [params' (assoc params :account-id
                             (u/str->uuid (:account-id params)))]
          (if-some [err (s/explain-data ads/admin-delete-params-spec params')]
            ;; Params fail spec - Bad Request
            (assoc (chain/terminate ctx)
                   :response
                   {:status 400
                    :body   {:error (format "Invalid parameters:\n%s"
                                            (-> err s/explain-out with-out-str))}})
            ;; Valid params - continue
            (let [acc-info (select-keys params' [:account-id])]
              (-> ctx
                  (assoc ::data acc-info)
                  (assoc-in [:request :session ::data] acc-info)))))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Intermediate Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def authenticate-admin
  (interceptor
   {:name ::authenticate-admin
    :enter
    (fn authenticate-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username password]} ::data}
            ctx
            {:keys [result]}
            (adp/-authenticate-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          ;; Pass it along as an intermediate value
          (uuid? result)
          (-> ctx
              (assoc-in [::data :account-id] result)
              (assoc-in [:request :session ::data :account-id] result))

          ;; The account is not in the table - Not Found
          (or (= :lrsql.admin/missing-account-error result)
              (= :lrsql.admin/invalid-password-error result))
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Invalid Account Credentials"}}))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terminal Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-admin
  "Create a new admin account and store it in the account table."
  (interceptor
   {:name ::create-admin
    :enter
    (fn create-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [username password]} ::data}
            ctx
            {:keys [result]}
            (adp/-create-account lrs username password)]
        (cond
          ;; The result is the account ID - success!
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id result}})

          ;; The account already exists - Conflict
          (= :lrsql.admin/existing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 409
                  :body   {:error (format "An account \"%s\" already exists!"
                                          username)}}))))}))

(def update-admin-password
  "Set a new password for an admin account."
  (interceptor
   {:name ::update-admin-password
    :enter
    (fn update-admin-password [ctx]
      (let [{lrs
             :com.yetanalytics/lrs
             {:keys [old-password new-password]}
             ::data
             {:keys [account-id]}
             :lrsql.admin.interceptors.jwt/data}
            ctx
            {:keys [result]}
            (adp/-update-admin-password lrs account-id old-password new-password)]
        (cond
          ;; The result is the account ID - success!
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id result}})

          ;; The given account-id does not belong to a known account
          (= :lrsql.admin/missing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404
                  :body   {:error (format "The account \"%s\" does not exist!"
                                          (u/uuid->str account-id))}})

          ;; The old password is not correct.
          (= :lrsql.admin/invalid-password-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Invalid Account Credentials"}}))))}))

(def delete-admin
  "Delete the selected admin account. This is a hard delete."
  (interceptor
   {:name ::delete-admin
    :enter
    (fn delete-admin [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::data}
            ctx
            oidc-enabled?
            (some? (:lrsql.admin.interceptors.oidc/admin-env ctx))
            {:keys [result]}
            (adp/-delete-account lrs account-id oidc-enabled?)]
        (cond
          ;; The result is the account ID - success!
          (uuid? result)
          (assoc ctx
                 :response
                 {:status 200 :body {:account-id account-id}})

          ;; The account was already deleted/missing - Not Found
          (= :lrsql.admin/missing-account-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 404
                  :body   {:error (format "The account \"%s\" does not exist!"
                                          (u/uuid->str account-id))}})
          ;; The account is the last local admin account and OIDC is off
          (= :lrsql.admin/sole-admin-delete-error result)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 400
                  :body   {:error (format "The account \"%s\" is the only local admin account and cannot be deleted!"
                                          (u/uuid->str account-id))}}))))}))

(def get-accounts
  "Get all admin accounts from the account table."
  (interceptor
   {:name ::get-accounts
    :enter
    (fn get-accounts [ctx]
      (let [{lrs :com.yetanalytics/lrs} ctx
            result (adp/-get-accounts lrs)]
        (assoc ctx
               :response
               {:status 200 :body result})))}))

(def me
  "Get the currently authenticated account."
  (interceptor
   {:name ::get-me
    :enter
    (fn get-accounts [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::jwt/data} ctx
            result (adp/-get-account lrs account-id)]
        (assoc ctx
               :response
               {:status 200 :body result})))}))

(def no-content
  "Return a 204 No Content response, without a body."
  (interceptor
   {:name ::get-no-content
    :enter
    (fn get-account [ctx]
      (assoc ctx :response {:status 204}))}))

;; JWT interceptors for admin

;; See also: `admin.interceptors.lrs-management/generate-one-time-jwt`
(defn generate-jwt
  "Upon account login, generate a new JSON web token."
  [secret exp ref leeway]
  (interceptor
   {:name ::generate-jwt
    :enter
    (fn generate-jwt [ctx]
      (let [{lrs :com.yetanalytics/lrs
             {:keys [account-id]} ::data}
            ctx
            json-web-token
            (admin-u/account-id->jwt account-id secret exp ref)]
        (adp/-purge-blocklist lrs leeway) ; Update blocklist upon login
        (assoc ctx
               :response
               {:status 200
                :body   {:account-id     account-id
                         :json-web-token json-web-token}})))}))

(defn renew-admin-jwt
  [secret exp]
  (interceptor
   {:name ::renew-jwt
    :enter
    (fn renew-jwt [ctx]
      (let [{{:keys [account-id refresh-exp]} ::jwt/data} ctx
            curr-time (u/current-time)]
        (if (jt/before? curr-time refresh-exp)
          (let [json-web-token (admin-u/account-id->jwt* account-id
                                                         secret
                                                         exp
                                                         refresh-exp)]
            (assoc ctx
                   :response
                   {:status 200
                    :body   {:account-id     account-id
                             :json-web-token json-web-token}}))
          (assoc (chain/terminate ctx)
                 :response
                 {:status 401
                  :body   {:error "Attempting JWT login after refresh expiry."}}))))}))

(def ^:private block-admin-jwt-error-msg
  "This operation is unsupported when `LRSQL_JWT_NO_VAL` is set to `true`.")

(defn block-admin-jwt
  "Add the current JWT to the blocklist. Return an error if we are in
   no-val mode."
  [exp leeway no-val?]
  (interceptor
   {:name ::add-jwt-to-blocklist
    :enter
    (fn add-jwt-to-blocklist [ctx]
      (if-not no-val?
        (let [{lrs :com.yetanalytics/lrs
               {:keys [jwt account-id]} ::jwt/data}
              ctx]
          (adp/-purge-blocklist lrs leeway) ; Update blocklist upon logout
          (adp/-block-jwt lrs jwt exp)
          (assoc (chain/terminate ctx)
                 :response
                 {:status 200
                  :body   {:account-id account-id}}))
        (assoc (chain/terminate ctx)
               :response
               {:status 400
                :body   {:error block-admin-jwt-error-msg}})))}))
