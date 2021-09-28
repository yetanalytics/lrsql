[<- Back to Index](index.md)

# Docker Image

Yet Analytics publishes Docker container images of SQL LRS on [DockerHub](https://hub.docker.com/repository/docker/yetanalytics/lrsql).

## Usage

You can run SQL LRS directly from the Docker CLI in its default SQLite configuration:

``` shell

$ docker run \
    -p 8080:8080 \
    -e LRSQL_API_KEY_DEFAULT=my_key \
    -e LRSQL_API_SECRET_DEFAULT=my_secret \
    -e LRSQL_ADMIN_USER_DEFAULT=my_username \
    -e LRSQL_ADMIN_PASS_DEFAULT=my_password \
    yetanalytics/lrsql:latest

```

### Other DBMSs

The SQL LRS Dockerfile uses the SQLite run script in `bin` as the default `CMD`. To change to another DBMS use the corresponding script. For instance to use PostgreSQL:

``` shell

$ docker run \
    -p 8080:8080 \
    -e LRSQL_API_KEY_DEFAULT=my_key \
    -e LRSQL_API_SECRET_DEFAULT=my_secret \
    -e LRSQL_ADMIN_USER_DEFAULT=my_username \
    -e LRSQL_ADMIN_PASS_DEFAULT=my_password \
    -e LRSQL_DB_HOST=0.0.0.0 \
    -e LRSQL_DB_PORT=5432 \
    -e LRSQL_DB_NAME=lrsql_db \
    -e LRSQL_DB_USER=lrsql_user \
    -e LRSQL_DB_PASSWORD=my_password \
    -e LRSQL_DB_SCHEMA=lrsql \
    yetanalytics/lrsql:latest \
    bin/run_postgres.sh

```

[<- Back to Index](index.md)
