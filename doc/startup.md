# Getting Started

## Startup instructions

1. [Install Java 11 or higher](https://java.com/en/download/help/download_options.html) if not installed already.

2. Download and unzip the SQL LRS package and place it in your favorite directory.

3. In order to support HTTPS, you should either generate a Java Keystore file or PEM files in order to establish a certificate chain. More details can be found [here](https.md).

4. Set configuration variables. Config vars can be set in `lrsql.json` or as environment variables; the following should be set:
- `apiKeyDefault` and `apiSecretDefault` MUST be set in order to create an initial admin account.
- `httpHost` should be set to the domain you are running your webserver on; likewise for `httpPort` and `httpsPort` (which by default is `8080` and `8443`, respectively).
- Likewise, `authorityUrl` should be set to a custom domain in order to uniquely identify Statements inserted into your LRS.
- If you are running PostgreSQL, you should set `dbHost`, `dbUser`, and `dbPassword` to the appropriate PostgreSQL system, as well as `dbProperties` if needed.
- If you are using Java Keystores, `keyFile` should point to a valid Keystore file; `keyAlias` and **especially** `keyPassword` should be overridden.

The `lrsql.json` file does not need to contain _all_ config vars; those that are skipped will use their default values.

The following is an example of a basic (non-Postgres) `lrsql.json` config file:

```json
{
    "lrs": {
        "apiKeyDefault": "this_is_the_default_key",
        "apiSecretDefault": "this_is_the_default_secret",
        "authorityUrl": "http://mydomain.com"
    },
    "webserver": {
        "keyAlias": "/user_config/keystore.jks",
        "keyPassword": "this_should_be_a_good_password",
        "httpHost": "mywebserverhost"
    }
}
```

Note that this config file does not set any non-default values in the `database` or `lrs` maps, so those are skipped entirely.

For a complete list of config variables, see [here](env_vars.md).

5. (Postgres only) Set up the Postgres DB; instructions for that can be found [here](postgres.md).

6. Start the LRS.
- Mac or Linux: Run the appropriate shell script from the command line, e.g. `./bin/run_sqlite.sh`. All scripts are located in the `bin` directory.
- Windows: Run the appropriate executable: `lrsql.exe` for a SQLite database, `lrsql_pg.exe` for a Postgres one.

## Your first Statement

xAPI Statements can be inserted into and queried from the SQL LRS using HTTP commands. Let us insert the following Statement:

```json
{
    "actor": {
        "mbox": "mailto:mike@example.org",
        "name": "Mike"
    },
    "verb": {
        "id": "http://example.org/verb/did",
        "display": {"en-US": "Did"}
    },
    "object": {
        "id": "http://example.org/activity/activity-1",
        "definition": {
            "name": {"en-US": "Activity 1"},
            "type": "http://example.org/activity-type/generic-activity"
        }
    }
}
```

In order to insert this Statement into your LRS, run the following command:
```
curl -X POST \
    http://[host]:[port]/xapi/statements \
    -H "Content-Type: application/json" \
    -H "X-Experience-API-Version: 1.0.3" \
    -u "[username]:[password]" \
    -d '[statement data]'
```
where `username` and `password` are your seed API keys, `host` and `port` are set for your webserver, and `statement data` is your Statement (which you can copy-paste from above). You should get a vector containing a single UUID back in return; this is the Statement ID that the SQL LRS generated (since the Statement did not have a pre-existing ID).

In order to retrieve that statement, you can run the following command:
```
curl -X GET \
    http://[host]:[port]/xapi/statements \
    -H "Content-Type: application/json" \
    -H "X-Experience-API-Version: 1.0.3" \
    -u "[username]:[password]"
```
This will run a query where all Statements will be returned - in this case, the one Statement we had just inserted.
