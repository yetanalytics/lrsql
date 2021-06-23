(ns lrsql.admin.protocol)

(defprotocol AdminAccountManager
  (-create-account [this account-map]
    "Create a new account with the username and password in `account-map`.")
  (-authenticate-account [this account-map]
    "Authenticate by looking up if the account exists in the account table.")
  (-delete-account [this account-map]
    "Delete the account and all associated tokens."))

(defprotocol APIKeyManager
  (-create-token [this account-map scopes]
    "Create a new token pair with the associated scopes.")
  (-update-token [this account-map token-pair scopes]
    "Update the token pair associated with the account with new scopes.")
  (-delete-token [this account-map token-pair]
    "Delete the token pair associated with the account."))
