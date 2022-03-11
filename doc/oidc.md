[<- Back to Index](index.md)

# OpenID Connect Support

SQL LRS supports [OpenID Connect (OIDC)](https://openid.net/connect/) on top of [OAuth 2.0](https://oauth.net/2/) for authentication and authorization to access xAPI resources and administrative functions. OIDC support enables several integration use cases:

* Send xAPI statements to the LRS from OIDC-authenticated client applications
* Provision LRS credentials and admin users programatically via the API
* Log in to the LRS Admin UI with a foreign identity (SSO)

## Resource Server

SQL LRS supports OIDC token authentication to all [endpoints](endpoints.md).

### xAPI Resources

### Admin API

## Admin UI Authentication

## Identity Providers

OIDC support is currently developed and tested against [Keycloak](https://www.keycloak.org/) but may work with other identity providers that implement the specification.


[<- Back to Index](index.md)
