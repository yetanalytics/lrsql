[<- Back to Index](index.md)

# Getting Started

### Startup instructions

#### 1. Download
- Download and unzip the SQL LRS package and place it in your preferred directory. Latest distributions can be found [here](https://github.com/yetanalytics/lrsql/releases)

#### 2. HTTPS Config
- In order to support HTTPS, SQL LRS automatically creates a temporary self-signed certificate upon startup. These are appropriate for testing and development, but not for a production context.
- If you have a valid TLS certificate you would like to use you can find more instructions for installing it [here](https.md).

#### 3. Configuration Variables
Create initial configuration variables. Config vars can be set by creating and editing the file `config/lrsql.json` in your SQL LRS directory or alternatively as environment variables; to start off with the following should be set:
- `adminUserDefault` and `adminPassDefault` MUST be set in order to create an initial admin account. Otherwise you will not be able to use the LRS. These are temporary credentials that you will use to log in the very first time.
- `httpHost` should be set to the host address you are running your webserver on if you are allowing network access (by default it is `0.0.0.0` for localhost); likewise for `httpPort` and `sslPort` (which by default are `8080` and `8443`, respectively).
- Likewise, `authorityUrl` should be set to a custom domain in order to uniquely identify Statements inserted into your LRS. (It is set to `http://example.org` by default.)
- If you are running Postgres, you should set `dbHost`, `dbName`, `dbUser`, and `dbPassword` to the appropriate Postgres system, as well as `dbPort`, `dbSchema` and `dbProperties` if needed.

The `config/lrsql.json` file does not need to contain _all_ config vars; those that are skipped will use their default values.

The following is an example of a basic (non-Postgres) `config/lrsql.json` config file:

```json
{
    "lrs": {
        "adminUserDefault": "myUsername",
        "adminPassDefault": "thisIsMyPassword1!1",
        "authorityUrl": "http://mydomain.com"
    },
    "webserver": {
        "httpHost": "0.0.0.0",
        "httpPort": 8080,
        "sslPort": 8443,
        "allowAllOrigins": true
    }
}
```
(Setting the `webserver` vars to these default values isn't necessary, but is shown here for demonstration purposes.)

The `allowAllOrigins` variable allows for a very permissive CORS configuration for testing and working locally in the absence of a permanent domain name/host. For a deployed system you will want to more properly configure CORS settings. For a complete list of config variables, see [here](env_vars.md). There is also a sample JSON config file provided for reference at `config/lrsql.json.example` which contains many more variables than the above.

#### 4. Setup Postgres DB (Optional)
- If using Postgres, setup instructions can be found [here](postgres.md).

#### 5. Start the LRS

The LRS uses a slightly different startup procedure depending on what SQL database you want running underneath it. The following table details those modes of operation.

| Mode | Linux/MacOS Script | Windows | Description |
| --- | --- | --- | --- |
| SQLite | `bin/run_sqlite.sh` | `lrsql.exe` | Run with a SQLite database on the filesystem. This is the typical run mode for an LRS without an external database. |
| Postgres | `bin/run_postgres.sh` | `lrsql_pg.exe` | Run with a separate Postgres Database. You must perform [additional configuration](postgres.md) to use this mode, and have a database already running and accessible by the LRS. |
| H2 In-Memory | `bin/run_h2.sh` | `n/a` | In-memory H2 db mostly used for development. This will not save your data after a restart. There is not a windows executable for this mode. |
| H2 Persistent | `bin/run_h2_persistent.sh` | `n/a` | H2 db mostly used for development. There is not a windows executable for this mode. |

*NOTE:* All attachments and documents have a maximum size of 1 megabyte in H2 (the maximum size of a [variable byte array](http://www.h2database.com/html/datatypes.html#binary_varying_type) in H2). As such, we strongly recommend using SQLite over H2 in production contexts.

##### Mac or Linux
- In terminal/shell navigate to the root of the SQL LRS directory
- Run the appropriate shell script from the command line, e.g. `bin/run_sqlite.sh`.

##### Windows
- Windows: Double click to run the appropriate executable: e.g. `lrsql.exe`

In either case you should see a command line output resembling the following, which indicates you have started up successfully.

![SQL LRS Startup](images/startup.png)

##### OS Security & Firewall Warnings

On some operating systems you will need to explicitly allow software from outside an app store to run the SQL LRS. For instance on MacOS:

![Mac Security Preferences](images/mac_sec_0.png)

Choose the "App Store and identified developers" option. The first time you run SQL LRS you will need to follow the procedure below to approve it.

During startup you may see a firewall/security warning depending on OS. In MacOS it may look like the following:

![Mac Security Warning 1](images/mac_sec_1.png)

In which case go to your settings and click Allow Anyway on this screen:

![Mac Security Warning 2](images/mac_sec_2.png)

In Windows you may see a similar warning that looks like this:

![Windows Security Warning](images/win_sec.png)

#### 6. Create Accounts and Credentials

Now that the LRS is running, let's set up a (non-seed) account and some credentials to use it.

- First navigate in a browser to the LRS address. This will be `http(s)://[host]:[port]/`. For example, if you did not change the host and port variables in the sections above, then by default it should be accessible at `http://0.0.0.0:8080/`. You should see a login screen much like the following:

![Login Page](images/login.png)

- Log into the LRS Admin UI using your seed admin account username and password that you set in `config/lrsql.json`.

- Under "Account Management," enter a new username and password and click "Create Account."

- Then log back in as the new account.

- Under "Credentials Management", click "Add New Credentials," which will auto-generate a new API key and secret.

#### 7. Delete Seed Account
For security purposes, it is important to delete the seed account (as the username and password are stored in plaintext). While logged in as the new account you just created in part 6, go to "Account Management," click the "Delete" button corresponding to the seed account.

![Delete Account](images/delete_seed.png)

Additionally you should remove the `adminUserDefault` and `adminPassDefault` lines from `config/lrsql.json`. This will prevent the system from re-creating the seed accounts on future restarts.

### Your first Statement

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
    -u "[api-key]:[api-secret]" \
    -d '[statement data]'
```
where `api-key` and `api-secret` are the credential pair you created earlier, `host` and `port` are set for your webserver, and `statement data` is your Statement (which you can copy-paste from above). This should return a JSON array containing a single UUID; this is the Statement ID that the SQL LRS generated (since the Statement did not have a pre-existing ID).

In order to retrieve that statement, you can run the following command:
```
curl -X GET \
    http://[host]:[port]/xapi/statements \
    -H "Content-Type: application/json" \
    -H "X-Experience-API-Version: 1.0.3" \
    -u "[api-key]:[api-secret]"
```
This will run a query where all Statements will be returned - in this case, the one Statement you had just inserted.

[<- Back to Index](index.md)
