[<- Back to Index](index.md)

# Configuration Variables

All configuration variables can either be set directly via the command line as environment variables, or can be added to the config file `config/lrsql.json` as a JSON object property.

### Logger

| Env Var           | Config     | Description                                                                                | Default |
| ----------------- | ---------- | ------------------------------------------------------------------------------------------ | ------- |
| `LRSQL_LOG_LEVEL` | `logLevel` | The logging level to use. Can be `ALL`, `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` or `OFF` | `INFO`  |

### Database

| Env Var               | Config         | Description                                                                                                                                                                                                                                                     | Default     |
| --------------------- | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| `LRSQL_DB_TYPE`       | `dbType`       | The DBMS that lrsql will use. Currently supports `sqlite`, and `postgres`/`postgresql`. The default value used depends on the `main` entry point used, e.g. `lrsql.sqlite.main` will use `sqlite` by default, so overriding the default is **not** recommended. | Varies      |
| `LRSQL_DB_NAME`       | `dbName`       | The name of the database. Should be set to `:memory:` for SQLite in-memory mode.                                                                                                                                                                                | `example`   |
| `LRSQL_DB_HOST`       | `dbHost`       | The host that the database will run on. Not supported by SQLite.                                                                                                                                                                                                | `0.0.0.0`   |
| `LRSQL_DB_PORT`       | `dbPort`       | The port that the database will run on. Not supported by SQLite.                                                                                                                                                                                                | `5432` (PG) |
| `LRSQL_DB_PROPERTIES` | `dbProperties` | Optional additional DB properties. Must be a string of key-val pairs that follow URL query parameter syntax (e.g. `key1=value1&key2=value2`). Any `&` character not used as a separator must be percent encoded. Supported properties will depend on the DBMS.  | Not set     |
| `LRSQL_DB_JDBC_URL`   | `dbJdbcUrl`    | Optional JDBC URL; this will override the above properties if set. URL syntax will depend on the DBMS.                                                                                                                                                          | Not set     |
| `LRSQL_DB_USER`       | `dbUser`       | The DB user. Optional.                                                                                                                                                                                                                                          | Not set     |
| `LRSQL_DB_PASSWORD`   | `dbPassword`   | The DB password. Optional.                                                                                                                                                                                                                                      | Not set     |
| `LRSQL_DB_SCHEMA`     | `dbSchema`     | The DB schema. Optional.                                                                                                                                                                                                                                        | Not set     |
| `LRSQL_DB_CATALOG`    | `dbCatalog`    | The DB catalog. Optional.                                                                                                                                                                                                                                       | Not set     |

### Connection

#### HikariCP Properties

The following environment variables are aliases for [HikariCP properties](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby). All of these variables (except for `poolName`) have default values that are already set, which may vary depending on the DBMS. All temporal values are in milliseconds.

| Env Var                                  | Config                          | Default              | Valid Values                                                                                                                                       |
| ---------------------------------------- | ------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LRSQL_POOL_AUTO_COMMIT`                 | `poolAutoCommit`                | `false`              | `true`/`false`                                                                                                                                     |
| `LRSQL_POOL_KEEPALIVE_TIME`              | `poolKeepaliveTime`             | `0`†                 | `≥ 10000` or `0`, less than `poolMaxLifetime`                                                                                                      |
| `LRSQL_POOL_CONNECTION_TIMEOUT`          | `poolConnectionTimeout`         | `3000`               | `≥ 250`                                                                                                                                            |
| `LRSQL_POOL_IDLE_TIMEOUT`                | `poolIdleTimeout`               | `600000`             | `≥ 10000` or `0`                                                                                                                                   |
| `LRSQL_POOL_VALIDATION_TIMEOUT`          | `poolValidationTimeout`         | `5000`               | `≥ 250`, less than `poolConnectionTimeout`                                                                                                         |
| `LRSQL_POOL_INITIALIZATION_FAIL_TIMEOUT` | `poolInitializationFailTimeout` | `1`                  | Any integer                                                                                                                                        |
| `LRSQL_POOL_MAX_LIFETIME`                | `poolMaxLifetime`               | `1800000` or `0`\*\* | `≥ 30000` or `0`                                                                                                                                   |
| `LRSQL_POOL_MINIMUM_IDLE`                | `poolMinimumIdle`               | `1`\* or `10`\*\*\*  | `≥ 0`                                                                                                                                              |
| `LRSQL_POOL_MAXIMUM_SIZE`                | `poolMaximumSize`               | `1`\* or `10`\*\*\*  | `≥ 1`                                                                                                                                              |
| `LRSQL_POOL_ISOLATE_INTERNAL_QUERIES`    | `poolIsolateInternalQueries`    | `false`              | `true`/`false`                                                                                                                                     |
| `LRSQL_POOL_LEAK_DETECTION_THRESHOLD`    | `poolLeakDetectionThreshold`    | `0`†                 | `≥ 2000` or `0`                                                                                                                                    |
| `LRSQL_POOL_TRANSACTION_ISOLATION`       | `poolTransactionIsolation`      | Not set              | [JDBC Connection constant](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/Connection.html) (e.g. `TRANSACTION_SERIALIZABLE`) |
| `LRSQL_POOL_NAME`                        | `poolName`                      | Not set              | Any string                                                                                                                                         |

\* SQLite default.
\*\* SQLite in-memory default.
\*\*\* Postgres default.
† The property is set to be disabled by default.

_NOTE 1:_ SQLite uses different defaults for `poolMinimumIdle` and `poolMaximumSize` than Postgres due to issues with multi-threading with those DBMSs. Setting `poolMaximumSize` to values other than `1` will potentially cause exceptions when running concurrent operations.

_NOTE 2:_ SQLite, while in in-memory mode, automatically deletes the database whenever the connection is removed, which can occur if a connection is closed and `poolMaxLifetime` is exceeded. Thus, `poolMaxLifetime` is set to `0`, denoting infinite connection lifetime, for convenience.

_NOTE 3:_ None of the DBMSs that SQL LRS currently supports allow for `TRANSACTION_NONE` as a `poolTransactionIsolation` value.

#### Metric Reporting via JMX

The following config var is to activate metrics reporting via JMX.

| Env Var                 | Config          | Description                         | Default |
| ----------------------- | --------------- | ----------------------------------- | ------- |
| `LRSQL_POOL_ENABLE_JMX` | `poolEnableJmx` | Activate metrics reporting via JMX. | `false` |

Unlike the previous vars, which are one-to-one with HikariCP properties, the following sets multiple such properties:

- `registerMbeans` is set in order to activate JMX reporting.
- `allowPoolSuspension` is set to `true` to allow for user control over connection pools.
- `metricRegistry` is set to be a Codahale/Dropwizard `MetricRegistry` instance.

#### Missing options?

You may have noted that some options are not available:

- `connectionTestQuery` is not recommended for JDBC4 (which all of our DBMS implementations use).
- `readOnly` will cause the SQL LRS to not work if set to `true`.
- `connectionInitSql` is set automatically by the SQL LRS system (e.g. to run pragmas in SQLite).
- `driverClassName` and `dataSource` would clash with SQL LRS's approach to setting the JDBC driver, which is via an URL.
- `healthCheckRegistry` cannot easily report via JMX, and most of its information should be covered by `metricRegistry` anyways.
- `threadFactory` and `scheduledExecutor` are Java instances that should only be used in specific execution environments.

### Tuning

The following options are used for advanced database performance tuning and may affect DB schema and performance. Use with caution.

| Env Var              | Config        | Description | Default |
| -------------------  | ------------- | ----------- | ------- |
| `LRSQL_ENABLE_JSONB` | `enableJsonb` | (Postgres Only) This option switches the `JSON` fields (including `xapi_statment.payload`) to `JSONB` datatype. Enabling this option increases the speed of some external JSON-based queries such as from a BI or reporting platform, but decreases statement write throughput. Be advised that changing this setting on a large existing database may result in a very long next startup time (possibly minutes) as all previous data gets converted. | `false` |

### LRS

| Env Var                         | Config                  | Description                                                                                                                                                                                                                              | Default                                                                               |
|---------------------------------|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `LRSQL_ADMIN_USER_DEFAULT`      | `adminUserDefault`      | The username of the account that seeds the account table, ie. added to the table upon initialization. Optional but **should be set in order to create other accounts. NOTE: Not subject to username length and character requirements.** | Not set                                                                               |
| `LRSQL_ADMIN_PASS_DEFAULT`      | `adminPassDefault`      | The password of the account that seeds the account table. Optional but **should be set in order to create other accounts. NOTE: Not subject to password length and character requirements. Please choose a secure password.**            | Not set                                                                               |
| `LRSQL_API_KEY_DEFAULT`         | `apiKeyDefault`         | The public API key that seeds the credential table. Optional, and is ignored if no seed admin account is set.                                                                                                                            | Not set                                                                               |
| `LRSQL_API_SECRET_DEFAULT`      | `apiSecretDefault`      | The secret API key that seeds the credential table. Optional, and is ignored if no seed admin account is set.                                                                                                                            | Not set                                                                               |
| `LRSQL_STMT_GET_DEFAULT`        | `stmtGetDefault`        | The default `limit` value in a statement query. Queries default to this value if not explicitly set.                                                                                                                                     | `50`                                                                                  |
| `LRSQL_STMT_GET_MAX`            | `stmtGetMax`            | The maximum allowed `limit` value for a statement query. If an explicit `limit` value exceeds this value, it will be overridden.                                                                                                         | `50`                                                                                  |
| `LRSQL_STMT_GET_MAX_CSV`        | `stmtGetMaxCsv`         | The maximum allowed `limit` value applied to a statement download. If an explicit `limit` value exceeds this value, it will be overridden. If negative (e.g. the default value of `-1`), it will be ignored and all statements in the LRS will be downloaded. | -1                                                                                   |
| `LRSQL_AUTHORITY_TEMPLATE`      | `authorityTemplate`     | The filepath to the Statement authority template file, which describes how authorities are constructed during statement insertion. If the file is not found, the system defaults to a default authority function.                        | <details>`config/authority.json.template`<summary>(Filepath)</summary></details>      |
| `LRSQL_AUTHORITY_URL`           | `authorityUrl`          | The URL that is set as the `authority-url` value when constructing an authority from a template.                                                                                                                                         | `http://example.org`                                                                  |
| `LRSQL_OIDC_AUTHORITY_TEMPLATE` | `oidcAuthorityTemplate` | Like `LRSQL_AUTHORITY_TEMPLATE`, but only used when forming an authority from an OIDC access token.                                                                                                                                      | <details>`config/oidc_authority.json.template`<summary>(Filepath)</summary></details> |
| `LRSQL_OIDC_SCOPE_PREFIX`       | `oidcScopePrefix`       | An optional prefix prepended to OIDC scope. For example, setting this to `lrs:` would change the expected `all` scope to `lrs:all`                                                                                                       | `""`                                                                                  |
| `LRSQL_STMT_RETRY_LIMIT`        | `stmtRetryLimit`        | The number of times to retry a statement post transaction before failing.                                                                                                                                                                | `10`                                                                                  |
| `LRSQL_STMT_RETRY_BUDGET`       | `stmtRetryBudget`       | The max amount of time allowed for statement POST transaction retries before failing (ms).                                                                                                                                               | `1000`                                                                                |
| `LRSQL_ENABLE_REACTIONS`        | `enableReactions`       | Whether or not to enable statement reactions.                                                                                                                                                                                            | `false`                                                                               |
| `LRSQL_REACTION_BUFFER_SIZE`    | `reactionBufferSize`    | Number of pending reactions to allow. Additional reactions will be dropped with a warning message.                                                                                                                                       | `10000`                                                                               |

_NOTE:_ `LRSQL_STMT_RETRY_LIMIT` and `LRSQL_STMT_RETRY_BUDGET` are used to mitigate a rare scenario where specific Actors or Activities are updated many times in large concurrent batches. In this situation the DBMS can encounter locking and these settings are used to allow retries that eventually write all the conflicting transactions, but may incur performance degradation. If you are experiencing this situation the first step would be to look at why your data needs to rewrite specific Actors or Activities rapidly with different values, which could potentially solve it at the source. If the issue cannot be avoided by data design alone, another possible solution is reducing batch sizes to decrease or eliminate locks. As a last resort, increasing these settings will at least ensure the statements get written but as mentioned may incur a slowdown in concurrent throughput.

### Webserver

#### Host & Ports

| Env Var                           | Config                   | Description                                                                                                                                                                                                                                            | Default   |
| --------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------- |
| `LRSQL_ENABLE_HTTP`               | `enableHttp`             | Whether HTTP is enabled or not (as opposed to HTTPS, which is always enabled).                                                                                                                                                                         | `true`    |
| `LRSQL_ENABLE_HTTP2`              | `enableHttp2`            | Whether HTTP/2 is supported or not.                                                                                                                                                                                                                    | `true`    |
| `LRSQL_HTTP_HOST`                 | `httpHost`               | The host that the webserver will run on.                                                                                                                                                                                                               | `0.0.0.0` |
| `LRSQL_HTTP_PORT`                 | `httpPort`               | The HTTP port that the webserver will be open on.                                                                                                                                                                                                      | `8080`    |
| `LRSQL_SSL_PORT`                  | `sslPort`                | The HTTPS port that the webserver will be open on.                                                                                                                                                                                                     | `8443`    |
| `LRSQL_URL_PREFIX`                | `urlPrefix`              | The prefix of the webserver URL path, e.g. the prefix in `http://0.0.0.0:8080/xapi` is `/xapi`. Used when constructing the `more` value for multi-statement queries. Cannot start with `/admin`. *(Note: Only applies to LRS xapi endpoints, not admin/ui endpoints)*              | `/xapi`   |
| `LRSQL_PROXY_PATH`                | `proxyPath`              | This path modification is exclusively for use with a proxy, such as apache or nginx or a load balancer, where a path is added to prefix the entire application (such as `https://www.mysystem.com/mylrs/xapi/statements`). This does not actually change the routes of the application, it informs the admin frontend where to look for the server endpoints based on the proxied setup, and thus must be used in conjunction with a third party proxy. If used, the value must start with a leading `/` but not end with one (e.g. `/mylrs` is valid, as is `/mylrs/b` but `/mylrs/` is not). Use with caution. | Not Set |
| `LRSQL_AUTH_BY_CRED_ID`                | `authByCredId`              | Allows a call to some xAPI endpoints (GET and POST) to use the admin account credentials/authorization instead of a call with raw xAPI credentials. Necessary when the xAPI endpoint is behind additional security/SSO and cannot have custom credentials in Authorization header. | false |

#### TLS/SSL Certificate

| Env Var                           | Config                   | Description                                                                                                                                                                                                                                            | Default                                                                                    |
| --------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| `LRSQL_KEY_FILE`                  | `keyFile`                | The path to the Java Keystore file that contains the key pair and credentials, which are used for HTTPS as well as JWT signing and verification.                                                                                                       | `config/keystore.jks`                                                                      |
| `LRSQL_KEY_ALIAS`                 | `keyAlias`               | The alias of the private key.                                                                                                                                                                                                                          | `lrsql_keystore`                                                                           |
| `LRSQL_KEY_PASSWORD`              | `keyPassword`            | The password protecting the keystore. **It is highly recommended that you override the default value.**                                                                                                                                                | `lrsql_pass`                                                                               |
| `LRSQL_KEY_PKEY_FILE`             | `keyPkeyFile`            | Private key in PEM format                                                                                                                                                                                                                              | `config/server.key.pem`                                                                    |
| `LRSQL_KEY_CERT_CHAIN`            | `keyCertChain`           | Comma separated PEM files for cert. See the TLS/HTTPS section below.                                                                                                                                                                                   | <details>`config/server.crt.pem,config/cacert.pem`<summary>(Filepaths)</summary></details> |
| `LRSQL_KEY_ENABLE_SELFIE`         | `keyEnableSelfie`        | Boolean, whether or not to enable self-signed cert generation.                                                                                                                                                                                         | `true`                                                                                     |

#### JWT Config

| Env Var                           | Config                   | Description                                                                                                                                                                                   | Default           |
| --------------------------------- | ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- |
| `LRSQL_JWT_EXP_TIME`              | `jwtExpTime`             | The amount of time, in seconds, after a JWT is created when it expires. **This this time should be short** (i.e. one hour or less).                             | `3600` (one hour) |
| `LRSQL_JWT_EXP_LEEWAY`            | `jwtExpLeeway`           | The amount of time, in seconds, before or after the expiration instant when a JWT should still count as un-expired. Used to compensate for clock desync. Applied to both LRS and OIDC tokens. | `1` (one second)  |
| `LRSQL_JWT_REFRESH_EXP_TIME`      | `jwtRefreshExpTime`      | The amount of time, in seconds, after a JWT is issued upon an initial login (_not_ a login renewal), after which login renewal can no longer be performed. Note: this is unaffected by expiration leeway. | `86400` (one day) |
| `LRSQL_JWT_REFRESH_INTERVAL`      | `jwtRefreshInterval`     | The amount of time after either a login or a previous JWT refresh that the client should poll the server in order to refresh the JWT. This **must** be less than `LRSQL_JWT_EXP_TIME`. | `3540` (59 minutes) |
| `LRSQL_JWT_INTERACTION_WINDOW`    | `jwtInteractionWindow`   | The amount of time before a potential JWT refresh that the client checks for interaction. If an interaction happens during this window, a JWT refresh will be performed the next time the client polls for one; no refresh happens if there is no interaction (or if an interaction happens before this window). This **must** be less than or equal to `LRSQL_JWT_REFRESH_INTERVAL`. | `600` (10 minutes) |
| `LRSQL_JWT_NO_VAL`                | `jwtNoVal`               | (**DANGEROUS!**) This flag removes JWT Token Validation and simply accepts token claims as configured by the associated variables below. It is extremely unlikely that you need this as it is for very specific proxy-overwrite authentication scenarios, and it poses a serious threat to system security if enabled. | `false` |
| `LRSQL_JWT_NO_VAL_UNAME`          | `jwtNoValUname`          | (**DANGEROUS!** See `LRSQL_JWT_NO_VAL`) This variable configures which claim key to use for the username when token validation is turned off.                     | Not Set |
| `LRSQL_JWT_NO_VAL_ISSUER`         | `jwtNoValIssuer`         | (**DANGEROUS!** See `LRSQL_JWT_NO_VAL`) This variable configures which claim key to use for the issuer when token validation is turned off.                       | Not Set |
| `LRSQL_JWT_NO_VAL_ROLE_KEY`       | `jwtNoValRoleKey`        | (**DANGEROUS!** See `LRSQL_JWT_NO_VAL`) This variable configures which claim key to look in for the role when token validation is turned off.                     | Not Set |
| `LRSQL_JWT_NO_VAL_ROLE`           | `jwtNoValRole`           | (**DANGEROUS!** See `LRSQL_JWT_NO_VAL`) This variable configures what role must be present in the key above when token validation is turned off.                  | Not Set |
| `LRSQL_JWT_NO_VAL_LOGOUT_URL`     | `jwtNoValLogoutUrl`      | (**DANGEROUS!** See `LRSQL_JWT_NO_VAL`) This variable specifies a logout URL that the client will redirect to on user logout when token validation is turned off. | Not Set |
| `LRSQL_JWT_COMMON_SECRET    `     | `jwtCommonSecret`        |  This variable serves as a secret key with which JWTs are signed. If this is unspecified, sql-lrs generates its own key, but when running multiple instances of sql-lrs behind load balancer, they need to accept each others' JWTs, meaning they need to be working from a common key.  This is a secret and should be secured as such.  It should be between 32 and 64 characters. | Not Set |

#### OIDC

This section is for optionally configuring OIDC Authentication/Authorization through a compatible third party access / identity management provider such as Keycloak. These variables have no effect if the first one, `LRSQL_OIDC_ISSUER`, is not set.

| Env Var                           | Config                   | Description                                                                                                                                                                         | Default |
| --------------------------------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `LRSQL_OIDC_ISSUER`               | `oidcIssuer`             | OIDC Issuer address used for [discovery](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig). Will enable OIDC if present.                                   | Not set |
| `LRSQL_OIDC_AUDIENCE`             | `oidcAudience`           | Optional OIDC audience for token claim verification.                                                                                                                                | Not set |
| `LRSQL_OIDC_CLIENT_ID`            | `oidcClientId`           | An optional OIDC client ID for the SQL LRS Admin SPA. If provided, along with the `LRSQL_OIDC_ISSUER` and `LRSQL_OIDC_AUDIENCE` variables, will enable OIDC access to the Admin UI. | Not Set |
| `LRSQL_OIDC_CLIENT_TEMPLATE`      | `oidcClientTemplate`     | An optional template to modify LRS Admin UI client OIDC configuration.                                                                                                              | Not Set |
| `LRSQL_OIDC_VERIFY_REMOTE_ISSUER` | `oidcVerifyRemoteIssuer` | Verify on startup that the issuer in remote configuration matches `LRSQL_OIDC_ISSUER`.                                                                                              | `true`  |
| `LRSQL_OIDC_ENABLE_LOCAL_ADMIN`   | `oidcEnableLocalAdmin`   | Whether or not to enable local administrative account authentication, login and management when `LRSQL_OIDC_ISSUER` is set.                                                         | `false` |

#### ClamAV Virus Scanning

This section is for optionally configuring [ClamAV antivirus scanning](https://www.clamav.net/) for inbound files.

| Env Var               | Config         | Description                                         | Default     |
|-----------------------|----------------|-----------------------------------------------------|-------------|
| `LRSQL_ENABLE_CLAMAV` | `enableClamav` | Whether or not to enable ClamAV antivirus scanning. | `false`     |
| `LRSQL_CLAMAV_HOST`   | `clamavHost`   | ClamAV Daemon Host                                  | `localhost` |
| `LRSQL_CLAMAV_PORT`   | `clamavPort`   | ClamAV Daemon Port                                  | `3310`      |

#### CORS

| Env Var                           | Config                   | Description                                                                                                                                                                                                                                            | Default |
| --------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------- |
| `LRSQL_ALLOW_ALL_ORIGINS`         | `allowAllOrigins`        | Determines whether to enable CORS. When false, it will not allow all origins, it will only allow either `LRSQL_HTTP_HOST` (for both HTTPS and HTTP ports) or if `LRSQL_ALLOWED_ORIGINS` is set that will override.                                     | `false` |
| `LRSQL_ALLOWED_ORIGINS`           | `allowedOrigins`         | This is a list of allowed origins which overrides the defaults. As an ENV it should be written as a comma separated list with no spaces. In JSON config it can be written directly as an array, e.g. `["http://myhost:myport", "https://anotherhost"]` | Not Set |

#### Browser Security Headers
These config vars enable and configure browser security response headers from the server. In all cases, not setting them results in no corresponding header, setting them to `"[default]"` results in a generic server default (specified below), and any other string manually overrides the header value.

| Env Var                       | Config               | Header Name                         | `[default]` value                     |
| ----------------------------- | -------------------- | ----------------------------------- | ------------------------------------- |
| `LRSQL_SEC_HEAD_HSTS`         | `secHeadHsts`        | `Strict-Transport-Security`         | `max-age=31536000; includeSubdomains` |
| `LRSQL_SEC_HEAD_FRAME`        | `secHeadFrame`       | `X-Frame-Options`                   | `DENY`                                |
| `LRSQL_SEC_HEAD_CONTENT_TYPE` | `secHeadContentType` | `X-Content-Type-Options`            | `nosniff`                             |
| `LRSQL_SEC_HEAD_XSS`          | `secHeadXss`         | `X-Xss-Protection`                  | `1; mode=block`                       |
| `LRSQL_SEC_HEAD_DOWNLOAD`     | `secHeadDownload`    | `X-Download-Options`                | `noopen`                              |
| `LRSQL_SEC_HEAD_CROSS_DOMAIN` | `secHeadCrossDomain` | `X-Permitted-Cross-Domain-Policies` | `none`                                |
| `LRSQL_SEC_HEAD_CONTENT`      | `secHeadContent`     | `Content-Security-Policy`           | `object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;` |

#### Admin Features

| Env Var                           | Config                   | Description                                                                                                                                                                                                      | Default |
| --------------------------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `LRSQL_ENABLE_ADMIN_DELETE_ACTOR` | `enableAdminDeleteActor` | Whether or not to enable the route and UI for deleting actors and related documents                                                                                                                              | `false` |
| `LRSQL_ENABLE_ADMIN_UI`           | `enableAdminUi`          | Whether or not to serve the administrative UI at `/admin`                                                                                                                                                        | `true`  |
| `LRSQL_ENABLE_ADMIN_STATUS`       | `enableAdminStatus`      | Whether or not to serve admin system status data that queries the database.                                                                                                                                      | `true`  |
| `LRSQL_ENABLE_STMT_HTML`          | `enableStmtHtml`         | Whether or not HTML data is returned in the LRS HTTP response if the Accept type is specified as text/html. If `false` disables HTML rendering regardless of Accept type. | `true`  |
| `LRSQL_ADMIN_LANGUAGE`            | `adminLanguage`          | Allows override of default RFC5646 language code (`en-US`). May require that you add new language map entries to `resources/lrsql/localization/language.json` to add a new language.                             | `en-US` |

[<- Back to Index](index.md)
