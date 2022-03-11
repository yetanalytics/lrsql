[<- Back to Index](index.md)

# OpenID Connect Support

SQL LRS supports [OpenID Connect (OIDC)](https://openid.net/connect/) on top of [OAuth 2.0](https://oauth.net/2/) for authentication and authorization to access xAPI resources and administrative functions. OIDC support enables several integration use cases:

* Send xAPI statements to the LRS from OIDC-authenticated client applications
* Provision LRS credentials and admin users programatically via the API
* Log in to the LRS Admin UI with a foreign identity (SSO)

### Resource Server

SQL LRS supports OIDC token authentication to all [endpoints](endpoints.md), allowing the use of an OIDC access token to make requests. In this context SQL LRS acts as an OAuth 2.0 "resource server".

To enable OIDC auth, set the `LRSQL_OIDC_ISSUER` (`oidcIssuer` in json) configuration variable to your identity provider's [Issuer Identifier](https://openid.net/specs/openid-connect-core-1_0.html#IssuerIdentifier) URI. This address must be accessible to the LRS on startup as it will perform [OIDC Discovery](https://openid.net/specs/openid-connect-discovery-1_0.html) to retrieve public keys and other information about the OIDC environment. It is also *strongly* recommended that you set the optional `LRSQL_OIDC_AUDIENCE` (`oidcAudience`) variable to the origin address of the LRS itself (ex. "http://0.0.0.0:8080") to enable verification that a given token was issued specifically for the LRS.

#### Scope

Resource authorization is determined by the scopes present in the token's `scope` claim. Note that the resource scopes discussed below can be prefixed with an arbitrary string by setting the `LRSQL_OIDC_SCOPE_PREFIX` (`oidcScopePrefix`). This is useful if the scopes used by SQL LRS might conflict with others used by your identity provider. For example, setting the variable to `lrs:` will change the `all` scope to `lrs:all`.

OIDC Clients making requests to SQL LRS will need to request the desired scopes, and it is responsibility of the identity provider to grant them (or not). Configuration of scopes and associated behavior varies by identity provider.

#### xAPI Resources

xAPI resources accept tokens with the following scopes:

| Scope              | Capability                                    |
| ---                | ---                                           |
| `all`              | Full read/write access to all xAPI resources. |
| `all/read`         | Read-only access to all xAPI resources.       |
| `statements/read`  | Read-only access to xAPI Statements           |
| `statements/write` | Write-only access to xAPI Statements          |

When SQL LRS accepts xAPI statements via OIDC auth it uses token claims to form the xAPI Authority. See [Authority Configuration](authority.md#oidc-authority) for more information.

#### Admin API Resources

Admin API resources share a single scope `admin` that represents full administrative control over SQL LRS.

Administrative functions like credential provisioning require a local admin account. After decoding the token SQL LRS will ensure that an account exists (or is created) with a `username` matching the token's `sub` claim and an `oidc_issuer` matching the `iss` claim. If the user exists but the issuer does not match the request will fail with a 401 status.

### Admin UI Authentication

The LRS Admin UI supports interactive login via an OIDC identity provider. To enable this functionality you must provide the OIDC issuer and audience (not optional in this case) as above and additionally set the `LRSQL_OIDC_CLIENT_ID` (`oidcClientId`) to the Client ID representing your Admin UI.

When enabled the LRS will send configuration to the Admin UI directing it to offer OIDC login. Click the OIDC login link to be redirected to your identity provider for login. Upon a successful login you will be redirected to the Admin UI.

#### Client Template

The LRS Admin UI uses [oidc-client-js](https://github.com/IdentityModel/oidc-client-js) to manage communication with the OIDC identity provider. For some providers it may be necessary to customize client configuration in which case the `LRSQL_OIDC_CLIENT_TEMPLATE` (`oidcClientTemplate`) variable can be set. Note that the `redirect_uri` and `post_logout_redirect_uri` will be set by the Admin UI client and should not be provided.

### Identity Providers

OIDC support is currently developed and tested against [Keycloak](https://www.keycloak.org/) but may work with other identity providers that implement the specification.

#### Keycloak Demo

This repository contains a Docker Compose file and configuration for a demo instance of keycloak that you can run locally to try out OIDC support. To run keycloak:

    cd dev-resources/keycloak_demo
    docker compose up

This will start a Keycloak server available at port 8081. You can adminster Keycloak via the [admin console](http://0.0.0.0:8081/auth/admin/master/console/) with the username `admin` and the password `changeme123`.

When Keycloak is up, start SQL LRS with the following config variables:

| Variable                  | Value                                  | Notes                                                             |
| ---                       | ---                                    | ---                                                               |
| `LRSQL_OIDC_ISSUER`       | `http://0.0.0.0:8081/auth/realms/test` | Keycloak realm uri.                                               |
| `LRSQL_OIDC_AUDIENCE`     | `http://0.0.0.0:8080`                  | The origin address of the LRS.                                    |
| `LRSQL_OIDC_CLIENT_ID`    | `lrs_admin_ui`                         | This is the ID of the preconfigured client in Keycloak.           |
| `LRSQL_OIDC_SCOPE_PREFIX` | `lrs:`                                 | Prefix scopes so general names like `all` do not cause collision. |

When SQL LRS has started navigate to the [Admin UI](http://0.0.0.0:8080/admin/index.html) and log in with the username `dev_user` and password `changeme123`.

[<- Back to Index](index.md)
