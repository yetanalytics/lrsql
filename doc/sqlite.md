[<- Back to Index](index.md)

# SQLite Database

When running SQL LRS in SQLite mode (via `bin/run_sqlite.sh` or `lrsql.exe`), the application will store all of its data in a local file it creates. This makes it very easy to manage the database. You can also direct any tools which are able to connect to or query SQLite databases to this file. By default the name of this file is `lrsql.sqlite.db` and it will appear in the root of the SQL LRS directory upon first startup.

### Changing the DB Name

If you wish to change the name of the db file that SQL LRS connects to, you can do so using a configuration variable. Changing `LRSQL_DB_NAME` (`dbName` in `config/lrsql.json`) will accomplish this. The example below shows how to direct SQL LRS to another file.

```json
{
  ...
  "database": {
    "dbName": "new-file-name.db"
  }
}
```

### Deleting the Database
Since the database is just a file, it should be no surprise that you can delete it and this will reset the LRS, starting over again from scratch. Alternatively changing the `dbName` variable as in the section above will keep the old file around but similarly allow you to start over fresh with a new one.

*WARNING:* Keep in mind there will be no ability to recover the LRS data if the file is deleted! This includes xAPI data as well as all accounts and credentials.

[<- Back to Index](index.md)
