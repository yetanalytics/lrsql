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

xAPI resources currently accept requests with the following scopes:

| Scope              | Capability                                    |
| ---                | ---                                           |
| `all`              | Full read/write access to all xAPI resources. |
| `all/read`         | Read-only access to all xAPI resources.       |
| `statements/read`  | Read-only access to xAPI Statements           |
| `statements/write` | Write-only access to xAPI Statements          |

When SQL LRS accepts xAPI statements via OIDC auth it uses token claims to form the xAPI Authority. See [Authority Configuration](authority.md#oidc-authority) for more information.

#### Admin API Resources

### Admin UI Authentication

### Identity Providers

OIDC support is currently developed and tested against [Keycloak](https://www.keycloak.org/) but may work with other identity providers that implement the specification.


[<- Back to Index](index.md)
