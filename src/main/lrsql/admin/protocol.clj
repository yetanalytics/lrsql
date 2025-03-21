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
  (-get-account [this account-id]
    "Get the account with the given ID exists in the account table. Returns a boolean.")
  (-delete-account [this account-id oidc-enabled?]
    "Delete the account and all associated creds. Assumes the account has already been authenticated. Requires OIDC status to prevent sole account deletion.")
  (-ensure-account-oidc [this username oidc-issuer]
    "Create or verify an existing admin account with the given username and oidc-issuer.")
  (-update-admin-password [this account-id old-password new-password]
    "Update the password for an admin account given old and new passwords."))

(defprotocol AdminJWTManager
  (-purge-blocklist [this leeway]
    "Purge the blocklist of any JWTs that have expired since they were added.")
  (-create-one-time-jwt [this jwt exp one-time-id]
    "Add a one-time JWT that will be blocked after it is validated.")
  (-block-jwt [this jwt expiration]
    "Block `jwt` and apply an associated `expiration` number of seconds. Returns an error if `jwt` is already in the blocklist.")
  (-block-one-time-jwt [this jwt one-time-id]
    "Similar to `-block-jwt` but specific to blocking one-time JWTs. Returns an error if `jwt` and `one-time-id` cannot be found or updated.")
  (-jwt-blocked? [this jwt]
    "Is `jwt` on the blocklist?"))

(defprotocol APIKeyManager
  (-create-api-keys [this account-id label scopes]
    "Create a new API key pair with the associated scopes.")
  (-get-api-keys [this account-id]
    "Get all API key pairs associated with the account.")
  (-update-api-keys [this account-id api-key secret-key label scopes]
    "Update the key pair associated with the account with new scopes.")
  (-delete-api-keys [this account-id api-key secret-key]
    "Delete the key pair associated with the account."))

(defprotocol AdminStatusProvider
  (-get-status [this params]
    "Get various LRS metrics."))

(defprotocol AdminReactionManager
  (-create-reaction [this title ruleset active]
    "Create a new reaction with the given title, ruleset and status.")
  (-get-all-reactions [this]
    "Return all reactions with any status.")
  (-update-reaction [this reaction-id title ruleset active]
    "Update a reaction with a new title, ruleset and/or active status")
  (-delete-reaction [this reaction-id]
    "Soft-delete a reaction."))

(defprotocol AdminLRSManager
  (-delete-actor [this params]
    "Delete actor by `:actor-id`")
  (-get-statements-csv [this writer property-paths params]
    "Retrieve statements by CSV. Instead of returning a sequence of
     statements, streams them to `writer` as a side effect, in order to
     avoid storing them in memory. `property-paths` are defined in the
     Reactions API, while `params` are the same query params for
     `-get-statements`."))
