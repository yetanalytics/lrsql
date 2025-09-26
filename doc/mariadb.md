[<- Back to Index](index.md)

# MariaDB

To run SQL-LRS with MariaDB, you need a properly configured MariaDB instance.  "Properly configured" really just means setting up a database, user, and password, and configuring SQL-LRS to connect to/with those.

## Environment Variables

You can configure MariaDB by setting the relevant environment variables.  Here is the `environment` entry from our [MariaDB docker-compose demo](https://github.com/yetanalytics/lrsql/blob/main/dev-resources/mariadb/docker-compose.yml)

```yml
    environment:
      MARIADB_ROOT_PASSWORD: lrsql_root_password 
      MARIADB_DATABASE: lrsql_db
      MARIADB_USER: lrsql_user
      MARIADB_PASSWORD: lrsql_password
```

Note that `MARIADB_ROOT_PASSWORD` is only required if running in a container; see the [MariaDB docs](https://mariadb.com/docs/server/server-management/install-and-upgrade-mariadb/installing-mariadb/binary-packages/automated-mariadb-deployment-and-administration/docker-and-mariadb/mariadb-server-docker-official-image-environment-variables#mariadb_root_password_hash-mariadb_root_password-mysql_root_password) for details.

The corresponding `lrsql.json` would look like

```json
{
  ...
  "database": {
    "dbHost": "0.0.0.0",
    "dbPort": 3306,
    "dbName": "lrsql_db",
    "dbUser": "lrsql_user",
    "dbPassword": "lrsql_password",
  }
}

```