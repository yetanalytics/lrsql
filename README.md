# lrsql

_lrsql (LER-skwəl) - The Learning Record Structured Query Language_

A SQL-based Learning Record Store.

## Overview

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in [Part 3 of the xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#partthree). lrsql is distinct from other LRS's developed at Yet Analytics for being entirely implemented in SQL, and supporting multiple SQL database management systems (DBMSs) like H2, SQLite, and Postgresql.

Currently, lrsql is installed by pulling the latest commit from its GitHub repo. In the future it will be available in public repositories like Clojars.

To use lrsql, a user needs to be authorized by an admin. Admin accounts can be created using special RESTful HTTP methods (described later in the README); logging into them will return a JSON Web Token (JWT), a temporary token that can then be used to create or access lrsql credentials. These credentials, which consist of a public API key (the "username"), a secret API key (the "password"), and their scopes ([described in the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#42-oauth-10-authorization-scope)), are then used as headers for LRS-specific methods to authenticate and authorize the request sender.

## Environment Variables

### Database

- `LRSQL_DB_TYPE`: The DBMS that lrsql will use. Currently supported values are `h2:mem` (default) and `h2`.
- `LRSQL_DB_NAME`: The name of the database. Default value is `example`.
- `LRSQL_DB_HOST`: The host that the database will run on. Default value is `localhost`.
- `LRSQL_DB_PORT`: The port that the database is open to. Default value is `8080`.
- `LRSQL_DB_SCHEMA`: The schema of all database tables. Default value is `lrsql`.

### LRS

- `LRSQL_SEED_API_KEY`: The public API key that seeds the credential table, ie. added to the table upon initialization. Optional and not set by default; this is primarily used for testing and development.
- `LRSQL_SEED_SECRET_KEY`: The secret API key that seeds the credential table, ie. added to the table upon initialization. Optional and not set by default; this is primarily used for testing and development.
- `LRSQL_STMT_MORE_URL_PREFIX`: A string that prefixes the fragment in the `more` URL returned by a multi-statement query. Default value is the empty string.
- `LRSQL_STMT_GET_DEFAULT`: The default `limit` value in a statement query. Queries default to this value if not explicitly set.
- `LRSQL_STMT_GET_MAX`: The maxiumum allowed `limit` value for a statement query. If an explicit `limit` value exceeds this value, it will be overriden.

### Webserver
- `LRSQL_KEY_FILE`: The path to the Java Keystore file that contains the key pair and credentials, which are used for HTTPS as well as JWT signing and verification. Default value is `config/keystore.jks`.
- `LRSQL_KEY_ALIAS`: The alias of the private key. Default value is `lrsql_keystore`.
- `LRSQL_KEY_PASSWORD`: The password protecting the keystore. Default value is `lrsql_pass`. **It is highly recommended that you override this value.**
- `LRSQL_JWT_EXPIRATION_TIME`: The amount of time, in seconds, after a JWT is created when it should expire. Set to `3600` (one hour) by default.
- `LRSQL_JWT_EXPIRATION_LEEWAY`: The amount of time, in seconds, before or after the expiration instant when a JWT should still count as un-expired. Used to compensate for clock desync. Set to `1` (one second) by default.
- `LRSQL_ENABLE_HTTP`: Whether HTTP is enabled or not (as opposed to HTTPS, which is always enabled). Default value is `true`.
- `LRSQL_ENABLE_HTTP2`: Whether HTTP/2 is supported or not. Default value is `true`.
- `LRSQL_HTTP_HOST`: The host that the webserver will run on. Default value is `0.0.0.0` (i.e. localhost).
- `LRSQL_HTTP_PORT`: The HTTP port that the webserver will be open on. Default value is `8080`.
- `LRSQL_SSL_PORT`: The HTTPS port that the webserver will be open on. Default value is `8443`.

## Makefile Targets

- `ci`: Called when running continuous integration; runs all test cases.
- `keystore`: Alias for the `config/keystore.jks`, which generates a Java Keystore file with the default alias, password, file path, and credentials. This is called during CI and is not recommended to generate keystores in production.
- `ephemeral`: Makes an in-memory H2 database with the seed API key `username` and seed API secret `password`. This can then be used during development to test/bench lrsql functionality.

## REST API

The HTTP methods that are LRS-specific are given in [the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#datatransfer). Requests to the LRS (which are denoted by the `xapi` path prefix) must contain a Base64 encoded, colon-separated private and secret key in the `authorization` field of the header. For example (assuming `http://example` is the URL body), `http://example.org/xapi/statements` is the URL at which the user inserts and queries xAPI statements; other URLs are used to insert and query documents, agents, and activities.

In addition to the LRS HTTP methods, lrsql supports methods for admin account creation, login, and use; these methods are denoted by the `admin` path prefix.

### Admin Account Routes

The following examples use `http://example.org` as the URL body. All methods require that the request body is a JSON object that contains `username` and `password` string values; otherwise, a `400 BAD REQUEST` response is returned. All methods return `200 OK` on success.

- `POST http://example.org/admin/account/create`: Create a new admin account. The response body contains a newly generated JSON Web Token on success. Returns a `400` error if the request body parameters are invalid, or a `409 CONFLICT` error if the account already exists.
- `POST http://example.org/admin/account/login`: Log into an existing account. The response body contains a newly generated JSON Web Token on success. A `404 NOT FOUND` error is returned if the account does not exist, or a `401 FORBIDDEN` error if the password is incorrect.
- `DELETE http://example.org/admin/account`: Delete an existing account. The response body is a message that says `"Successfully delete account [username]"` on success. Returns a `404 NOT FOUND` error if the account does not exist, or a `401 FORBIDDEN` error if the password is incorrect.

### Admin Credential Routes

The following examples use `http://example.org` as the URL body. All methods require that the `authorization` header value is a valid JSON Web Token generated by account creation or login. All methods return a `401 FORBIDDEN` error if the JWT has expired, or a `400 BAD REQUEST` error if the JWT is otherwise invalid. All methods also require that the request body is a JSON object, though the permitted values depend on the route; otherwise, a `400 BAD REQUEST` error is returned.

- `PUT http://example.org/creds`: Create a new credential pair, with the specified scope values given by the `scopes` property in the request body.
- `POST http://example.org/creds`: Update an existing credential pair, given by `api-key` and `secret-key` properties in the request body, with the new scopes given by the `scopes` property.
- `GET http://example.org/creds`: Read all credential pairs and their associated scopes for a particular account (dentoed by the JWT).
- `DELETE http://example.org/creds`: Delete an existing credential pair, given by the `api-key` and `secret-key` properties in the request body, as well as any associated scopes.

