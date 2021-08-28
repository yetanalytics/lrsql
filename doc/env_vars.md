# Environment Variables

All environment variables can either be set directly via the command line, or can be added to the config file `config/lrsql.json` as a JSON object property.

## Database

| Env Var | Config | Description | Default |
| --- | --- | --- | --- |
| `LRSQL_DB_TYPE` | `dbType` | The DBMS that lrsql will use. Currently supports `h2:mem`, `h2`, `sqlite`, and `postgres`/`postgresql`. The default value used depends on the `main` entry point used, e.g. `lrsql.sqlite.main` will use `sqlite` by default, so overriding the default is **not** recommended. | Varies |
| `LRSQL_DB_NAME` | `dbName` | The name of the database. | `example` |
| `LRSQL_DB_HOST` | `dbHost` | The host that the database will run on. Not supported by in-mem H2 or SQLite. | `localhost` |
| `LRSQL_DB_PORT` | `dbPort` | The port that the database will run on. Not supported by in-mem H2 or SQLite. | `9001` (H2), `5432` (PG) |
| `LRSQL_DB_PROPERTIES` | `dbProperties` | Optional additional DB properties. Must be a string of key-val pairs that follow URL query parameter syntax (e.g. `key1=value1&key2=value2`). Any special characters (except for `,` and `:`) must be percent encoded. Supported properties will depend on the DBMS. | Not set |
| `LRSQL_DB_JDBC_URL` | `dbJdbcUrl` | Optional JDBC URL; this will override the above properties if set. URL syntax will depend on the DBMS. | Not set |
| `LRSQL_DB_USER` | `dbUser` | The DB user. Optional. | Not set |
| `LRSQL_DB_PASSWORD` | `dbPassword` | The DB password. Optional. | Not set |

## Connection

The following environment variables are aliases for c3p0 properties, each of which has their respective link to the c3p0 documentation. All of these variables are optional and are not set by default (in which case c3p0 uses its own default values). Note that SQLite uses its own defaults due to issues with multi-threading.

| Env Var | Config | c3p0 Property | c3p0 Default | SQLite Default |
| --- | --- | --- | --- | --- |
| `LRSQL_POOL_INIT_SIZE` | `poolInitSize` |  [initialPoolSize](https://www.mchange.com/projects/c3p0/#initialPoolSize) | 3 | 1 |
| `LRSQL_POOL_MIN_SIZE` | `poolMinSize` | [minPoolSize](https://www.mchange.com/projects/c3p0/#minPoolSize) | 3 | 1 |
| `LRSQL_POOL_INC` | `poolInc` | [acquireIncrement](https://www.mchange.com/projects/c3p0/#acquireIncrement) | 3 | 1 |
| `LRSQL_POOL_MAX_SIZE` | `poolMaxSize` | [maxPoolSize](https://www.mchange.com/projects/c3p0/#maxPoolSize) | 15 | 1 |
| `LRSQL_POOL_MAX_STMTS` | `poolMaxStmts` | [maxStatements](https://www.mchange.com/projects/c3p0/#maxStatements) | 0 | 0 |

## LRS

| Env Var | Config | Description | Default |
| --- | --- | --- | --- |
| `LRSQL_API_KEY_DEFAULT` | `apiKeyDefault` | The public API key that seeds the credential table, ie. added to the table upon initialization. Optional **but shouold be set**. | Not set |
| `LRSQL_API_SECRET_DEFAULT` | `apiSecretDefault` | The secret API key that seeds the credential table, ie. added to the table upon initialization. Optional **but should be set**. | Not set |
| `LRSQL_STMT_GET_DEFAULT` | `stmtGetDefault` | The default `limit` value in a statement query. Queries default to this value if not explicitly set. | `50` |
| `LRSQL_STMT_GET_MAX` | `stmtGetMax` | The maximum allowed `limit` value for a statement query. If an explicit `limit` value exceeds this value, it will be overridden. | `50` |
| `LRSQL_AUTHORITY_TEMPLATE` | `authorityTemplate` | The filepath to the Statement authority template file, which describes how authorities are constructed during statement insertion. If the file is not found, the system defaults to a default authority function. | <details>`config/authority.json.template`<summary>(Long string)</summary></details> |
| `LRSQL_AUTHORITY_URL` | `authorityUrl` | The URL that is set as the `authority-url` value when constructing an authority from a template. | `http://localhost` |

## Webserver

| Env Var | Config | Description | Default |
| --- | --- | --- | --- |
| `LRSQL_KEY_FILE` | `keyFile` | The path to the Java Keystore file that contains the key pair and credentials, which are used for HTTPS as well as JWT signing and verification. | `config/keystore.jks` |
| `LRSQL_KEY_ALIAS` | `keyAlias` | The alias of the private key. | `lrsql_keystore` |
| `LRSQL_KEY_PASSWORD` | `keyPassword` | The password protecting the keystore. **It is highly recommended that you override the default value.** | `lrsql_pass` |
| `LRSQL_KEY_PKEY_FILE` | `keyPkeyFile` | Private key in PEM format | `config/server.key.pem` |
| `LRSQL_KEY_CERT_CHAIN` | `keyCertChain` | Comma separated PEM files for cert. See the TLS/HTTPS section below. | <details>`config/server.crt.pem,config/cacert.pem`<summary>(Long string)</summary></details> |
| `LRSQL_KEY_ENABLE_SELFIE` | `keyEnableSelfie` | Boolean, whether or not to enable self-signed cert generation. | `true` |
| `LRSQL_JWT_EXP_TIME` | `jwtExpTime` | The amount of time, in seconds, after a JWT is created when it expires. Since JWTs are not revocable, **this this time should be short** (i.e. one hour or less). | `3600` (one hour) |
| `LRSQL_JWT_EXP_LEEWAY` | `jwtExpLeeway` | The amount of time, in seconds, before or after the expiration instant when a JWT should still count as un-expired. Used to compensate for clock desync. | `1` (one second) |
| `LRSQL_ENABLE_HTTP` | `enableHttp` | Whether HTTP is enabled or not (as opposed to HTTPS, which is always enabled). | `true` |
| `LRSQL_ENABLE_HTTP2` | `enableHttp2` | Whether HTTP/2 is supported or not. | `true` |
| `LRSQL_HTTP_HOST` | `httpHost` | The host that the webserver will run on. | `0.0.0.0` (localhost) |
| `LRSQL_HTTP_PORT` | `httpPort` | The HTTP port that the webserver will be open on. | `8080` |
| `LRSQL_SSL_PORT` | `sslPort` | The HTTPS port that the webserver will be open on. | `8443` |
| `LRSQL_URL_PREFIX` | `urlPrefix` | The prefix of the webserver URL path, e.g. the prefix in `http://localhost/xapi` is `/xapi`. Used when constructing the `more` value for multi-statement queries. | `/xapi` |
