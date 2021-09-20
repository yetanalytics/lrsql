[<- Back to Index](index.md)

# Postgres setup

Using the Postgres implementation of the SQL LRS requires a pre-existing database (unlike the H2 and SQLite implementations, which create the database file if it does not exist). Therefore, you need to set up the Postgres user and database before you start using the SQL LRS.

#### 1. Log into `psql` as a user with user and database creation permissions.

#### 2. Create User

Create the user that the SQL LRS will use (note the single quotes around the password):
```
postgres=# CREATE USER [username] WITH CREATEDB WITH PASSWORD '[password]';
```

#### 3. Create Database
Log into `psql` as the new user and create the underlying database that SQL LRS will use:
```
% psql -U [username]
[username]=# CREATE DATABASE [db_name];
```

#### 4. Create Schema (Optional, but recommended)

Connect to the database and create a new schema for all the database objects:
```
% psql -d [db_name]
[db_name]=# CREATE SCHEMA IF NOT EXISTS [schema_name];
```

You must then set the schema search path, where the first schema listed is the one that you just created. You can do so for the user:
```
postgres=# ALTER ROLE [username] SET search_path TO [search_path];
```

Or you can fix it for the database:
```
[username]=# ALTER DATABASE [db_name] SET search_path TO [search_path];
```

Note that the above changes will only affect subsequent Postgres sessions, not the current one.

You can also set the search path as the value of the `currentSchema` property, which you can do in `lrsql.json`:
```json
{
  ...
  "database": {
    ...
    "dbProperties": "currentSchema=[schema_name]"
  }
}
```

If you skip this step, then the default `public` schema will be used for all DB objects.

#### 5. Start SQL LRS and enjoy!

Startup instructions can be found [here](startup.md)

## Example lrsql.json configuration

Here is an example database config map in the `lrsql.json` configuration file. The user is `lrsql_user`, the password is `this_should_be_a_good_password`, and the schema is `lrsql`. The host is set to `myhost`, while the port is maintained at the Postgres default of `5432` (which is why it is not included in the sample).

```json
{
  ...
  "database": {
    "dbHost": "myhost",
    "dbUser": "lrsql_user",
    "dbPassword":  "this_should_be_a_good_password",
    "dbProperties": "currentSchema=lrsql"
  }
}
```

The `connection`, `lrs`, and `webserver` sections of the config can then be set with whatever properties you see fit, like the example on the [Getting Started](startup.md) page.

[<- Back to Index](index.md)
