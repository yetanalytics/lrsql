[<- Back to Index](index.md)

# MySQL

SQL-LRS supports MySQL.

To run SQL-LRS with MySQL, you need a properly configured MySQL instance.  "Properly configured" really just means setting up a database, user, and password, and configuring SQL-LRS to connect to/with those.

## Environment Variables

You can configure MySQLDB by setting the relevant environment variables.  Here is the `environment` entry from our [MySQL docker-compose demo](https://github.com/yetanalytics/lrsql/blob/main/dev-resources/mysql/docker-compose.yml)

```yml
    environment:
      MYSQL_ROOT_PASSWORD: lrsql_root_password 
      MYSQL_DATABASE: lrsql_db
      MYSQL_USER: lrsql_user
      MYSQL_PASSWORD: lrsql_password
```

(`MYSQL_ROOT_PASSWORD` can be anything, as SQL-LRS doesn't use the root account.  It is only included here because the MySQL Docker container requires a root password setting.  See the [MySQL docs](https://dev.mysql.com/doc/refman/8.4/en/docker-mysql-more-topics.html] for details)

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

## Precision Limitation

SQL-LRS stores statements in its implementation databases as JSON.  Due to the way MySQL interprets numbers in JSON, we **cannot guarantee more than 15 significant digits of precision** when using SQL-LRS alongside MySQL.  If you need that much precision, consider using SQL-LRS alongside [Postgres](postgres.md) or [MariaDB](mariadb.md) instead.