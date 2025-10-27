[<- Back to Index](index.md)

# MySQL

SQL-LRS supports MySQL, a sister database to MariaDB.  

To run SQL-LRS with MySQL, you need a properly configured MySQL instance.  "Properly configured" really just means setting up a database, user, and password, and configuring SQL-LRS to connect to/with those.

## Environment Variables

You can configure MySQL by setting the relevant environment variables.  The settings relevant to SQL-LRS are:

 - `MYSQL_DATABASE` (should match  `lrsql_db` in `lrsql.json`)
 - `MYSQL_USER` (should match  `lrsql_user` in `lrsql.json`)
 - `MYSQL_PASSWORD` (should match  `lrsql_password` in `lrsql.json`)


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

## Running



## Precision Limitation

SQL-LRS stores statements in its partner databases as JSON.  Due to the way MySQL interprets numbers in JSON, we **cannot guarantee more than 15 significant digits of precision** when using SQL-LRS alongside MySQL.  If you need that much precision, consider using SQL-LRS alongside [Postgres](postgres.md) or [MariaDB](mariadb.md) instead.