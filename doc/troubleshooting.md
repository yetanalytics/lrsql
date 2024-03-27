[<- Back to Index](index.md)

# Troubleshooting

### I am unable to connect to the LRS when opening the UI

First of all, ensure that the SQL LRS app is running and that the host and port are configured correctly. If you are running SQL LRS as a Docker image, ensure that the port is exposed.

In addition, you may need to adjust [configuration](env_vars.md#cors) for CORS (Cross-Origin Resource Sharing), which restricts which endpoints requests can be sent from and be accepted by the LRS. Either specify allowed endpoint URLs via `LRSQL_ALLOWED_ORIGINS` (the recommended way for production instances) or allow all endpoints via setting `LRSQL_ALLOW_ALL_ORIGINS` to `true`.

### I am unable to run the Docker image in Postgres mode

First of all, ensure that you are indeed executing the SQL LRS image in Postgres mode. The command `/lrsql/bin/run_postgres.sh` needs to be run as a custom command in order to override the default command, which runs the app in SQLite mode.

In addition, check that you have the appropriate values of `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` for your Postgres Docker image, and that the respective config values (`LRSQL_DB_NAME`, `LRSQL_DB_USER`, and `LRSQL_DB_PASSWORD`) match up.

See the `docker-compose.yml` file as a reference for running Postgres SQL LRS via Docker/Docker Compose.

### My Postgres connections don't get released when not in use

You may want to adjust the `LRSQL_POOL_MINIMUM_IDLE` config var, as it is set to 10 by default in Postgres mode. (See [here](env_vars.md#hikaricp-properties) for more info on connection pool configuration.)

[<- Back to Index](index.md)
