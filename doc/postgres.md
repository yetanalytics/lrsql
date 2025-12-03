[<- Back to Index](index.md)

# Postgres Database

### Setup Instructions

Using the Postgres implementation of the SQL LRS requires a pre-existing database (unlike the SQLite implementations, which create the database file if it does not exist). Therefore, you need to set up the Postgres user and database before you start using the SQL LRS.

#### 1. Create User

Start `psql` as a user with user and database creation permissions. Create the user that the SQL LRS will use (note the single quotes around the password):

```
% psql
postgres=# CREATE USER [username] WITH CREATEDB WITH PASSWORD '[password]';
```

#### 2. Create Database

Start `psql` as the new user and create the underlying database that SQL LRS will use:

```
% psql -U [username]
[username]=# CREATE DATABASE [db_name];
```

#### 3. Create Schema (Optional, but recommended)

This step is for creating and using a new Postgres [schema](https://www.postgresql.org/docs/13/ddl-schemas.html). If you skip this step, then the default `public` schema will be used for all DB objects.

Connect to the database and create a new schema for all the database objects:

```
% psql -d [db_name]
[db_name]=# CREATE SCHEMA IF NOT EXISTS [schema_name];
```

Then you can set the `LRSQL_DB_SCHEMA` (`dbSchema` in `config/lrsql.json`) config var to that schema; the JDBC driver will automatically use that schema during DB operation:

```json
{
  ...
  "database": {
    ...
    "dbSchema": "[schema_name]"
  }
}
```

You can also manually set the `search_path` property, which lists one or more schemas for Postgres to recognize. An example `search_path` is `my_schema,public`; that will tell Postgres to search for tables, indexes, and other objects in the `my_schema` schema, then in the default `public` schema if needed.

One way to set the search path is by setting the value of the `currentSchema` property, which you can do by setting `LRSQL_DB_PROPERTIES` (`dbProperties` in `config/lrsql.json`):

```json
{
  ...
  "database": {
    ...
    "dbProperties": "currentSchema=[search_path]"
  }
}
```

You can also fix `search_path` for the user in `psql`:

```
postgres=# ALTER ROLE [username] SET search_path TO [search_path];
```

Or fix it for the database:

```
[username]=# ALTER DATABASE [db_name] SET search_path TO [search_path];
```

Note that the above changes will only affect subsequent Postgres sessions, not the current one.

#### 4. Start SQL LRS and enjoy!

Startup instructions can be found [here](startup.md)

### Example lrsql.json configuration

Here is an example database config map in `config/lrsql.json`. The user is `lrsql_user`, the password is `my_password`, the DB name is `lrsql_db`, and the schema is `lrsql`. The host is set to `0.0.0.0` while the port is set to `5432` (technically not needed here since these are Postgres defaults, but they are provided here for demonstration).

```json
{
  ...
  "database": {
    "dbHost": "0.0.0.0",
    "dbPort": 5432,
    "dbName": "lrsql_db",
    "dbUser": "lrsql_user",
    "dbPassword": "my_password",
    "dbSchema": "lrsql"
  }
}
```

The `connection`, `lrs`, and `webserver` sections of the config can then be set with whatever properties you see fit, like the example on the [Getting Started](startup.md) page.

### Supported Versions

SQL LRS supports PostgreSQL versions 14 through 17. It is tested against the [current minor point release](https://www.postgresql.org/support/versioning/) of each version.

### Unix Socket Support

SQL LRS includes [junixsocket](https://github.com/kohlschutter/junixsocket) which allows unix socket connections to Postgres.

To connect via a unix socket use a JDBC URL like `jdbc:postgresql://localhost/test-postgres-db?socketFactory=org.newsclub.net.unix.AFUNIXSocketFactory%24FactoryArg&socketFactoryArg=/var/run/postgresql/.s.PGSQL.5432&user=test-postgres-user` where `socketFactoryArg` is the path to your Postgres socket file and `user` is your DB user. See the entry for `LRSQL_DB_JDBC_URL` [here](env_vars.md) for information on setting the JDBC URL.

[<- Back to Index](index.md)
