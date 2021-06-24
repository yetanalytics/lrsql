(ns lrsql.admin.protocol)

;; TODO: Async versions

(defprotocol AdminAccountManager
  (-create-account [this username password]
    "Create a new account with `username` and `password`.")
  (-authenticate-account [this username password]
    "Authenticate by looking up if the account exists in the account table.")
  (-delete-account [this account-id]
    "Delete the account and all associated tokens. Assumes the account has already been authenticated."))

(defprotocol APIKeyManager
  (-create-api-keys [this account-id scopes]
    "Create a new API key pair with the associated scopes.")
  (-get-api-keys [this account-id]
    "Get all API key pairs associated with the account.")
  (-update-api-keys [this account-id key-pair scopes]
    "Update the key pair associated with the account with new scopes.")
  (-delete-api-keys [this account-id key-pair]
    "Delete the key pair associated with the account."))
