(ns lrsql.admin.protocol)

(defprotocol AdminAccountManager
  (-create-account [this username password]
    "Create a new account with `username` and `password`.")
  (-get-accounts [this]
    "Get all admin user accounts")
  (-authenticate-account [this username password]
    "Authenticate by looking up if the account exists in the account table.")
  (-existing-account? [this account-id]
    "Check that the account with the given ID exists in the account table. Returns a boolean.")
  (-delete-account [this account-id oidc-enabled?]
    "Delete the account and all associated creds. Assumes the account has already been authenticated. Requires OIDC status to prevent sole account deletion.")
  (-ensure-account-oidc [this username oidc-issuer]
    "Create or verify an existing admin account with the given username and oidc-issuer.")
  (-update-admin-password [this account-id old-password new-password]
    "Update the password for an admin account given old and new passwords."))

(defprotocol APIKeyManager
  (-create-api-keys [this account-id scopes]
    "Create a new API key pair with the associated scopes.")
  (-get-api-keys [this account-id]
    "Get all API key pairs associated with the account.")
  (-update-api-keys [this account-id api-key secret-key scopes]
    "Update the key pair associated with the account with new scopes.")
  (-delete-api-keys [this account-id api-key secret-key]
    "Delete the key pair associated with the account."))

(defprotocol AdminStatusProvider
  (-get-status [this params]
    "Get various LRS metrics."))
