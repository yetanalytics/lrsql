[<- Back to Index](index.md)

# Docker Image

Yet Analytics publishes Docker container images of SQL LRS on [DockerHub](https://hub.docker.com/repository/docker/yetanalytics/lrsql) in the format `yetanalytics/lrsql:<release version | latest>`.

### Usage

You can run SQL LRS directly from the Docker CLI in its default SQLite configuration:

``` shell
docker run \
    -it \
    -p 8080:8080 \
    -e LRSQL_API_KEY_DEFAULT=my_key \
    -e LRSQL_API_SECRET_DEFAULT=my_secret \
    -e LRSQL_ADMIN_USER_DEFAULT=my_username \
    -e LRSQL_ADMIN_PASS_DEFAULT=my_password \
    -e LRSQL_DB_NAME=db/lrsql.sqlite.db \
    -v lrsql-db:/lrsql/db \
    yetanalytics/lrsql:latest
```

After SQL LRS starts and you see the logo, navigate to [http://0.0.0.0:8080/admin](http://0.0.0.0:8080/admin) to access the UI. Note that the `-it` option will give you a pseudo-TTY and attach you to the container, allowing you to stop the SQL LRS container with ^C. It is not needed for production use, where `-d` would be preferable. See the [docker run docs](https://docs.docker.com/engine/reference/commandline/run/) for more information.

The data from SQL LRS will be persisted to the Docker [named volume](https://docs.docker.com/engine/reference/run/#volume-shared-filesystems) supplied with the `-v` option, `lrs-db`. Note that `LRSQL_DB_NAME` is also set to write the database file to the volume.

#### Runtime Configuration Files

Docker's volume mount feature can also be used to supply customized configuration from a directory on your filesystem:

``` shell
docker run \
    -it \
    -p 8080:8080 \
    -v /home/alice/my_custom_config:/lrsql/config \
    yetanalytics/lrsql:latest
```

This is the suggested method for supplying a custom JSON configuration file, authority template or TLS certificate to SQL LRS.

See [Getting Started](startup.md) for more information on configuration files [Configuration Variables](env_vars.md) for a full list of settings.

#### Other DBMSs

The SQL LRS Dockerfile uses the SQLite run script in `bin` as the default `CMD`. To change to another DBMS use the corresponding script. For instance to use Postgres:

``` shell
docker run \
    -it \
    -p 8080:8080 \
    -e LRSQL_API_KEY_DEFAULT=my_key \
    -e LRSQL_API_SECRET_DEFAULT=my_secret \
    -e LRSQL_ADMIN_USER_DEFAULT=my_username \
    -e LRSQL_ADMIN_PASS_DEFAULT=my_password \
    -e LRSQL_DB_HOST=0.0.0.0 \
    -e LRSQL_DB_PORT=5432 \
    -e LRSQL_DB_NAME=lrsql_db \
    -e LRSQL_DB_USER=lrsql_user \
    -e LRSQL_DB_PASSWORD=lrsql_password \
    yetanalytics/lrsql:latest \
    /lrsql/bin/run_postgres.sh
```

Note that you will also need to ensure that a Postgres instance is accessible to the container for this command to work. For a demonstration of containerized SQL LRS with Postgres you can use the `docker-compose.yml` file in the project root:

``` shell
docker compose up
```

Docker will start Postgres and then SQL LRS. Note that Postgres can sometimes take a while to start causing SQL LRS initialization to fail. If this happens, stop the system with ctrl-C and run the command again, optionally increasing the value of `LRSQL_POOL_INITIALIZATION_FAIL_TIMEOUT` to allow more time before the SQL LRS declares a connection failure (see `docker-compose.yml` file).

### Customization

The SQL LRS image can be used as a base image for a customized docker image. This allows customizations such as a TLS certificate, configuration file, or custom authority.

For instance, to make an image with custom configuration, certs and authority:

``` dockerfile
FROM yetanalytics/lrsql:latest

# custom configuration
ADD my_lrsql.json              /lrsql/config/lrsql.json

# custom certs
ADD my_server.key.pem          /lrsql/config/server.key.pem
ADD my_server.crt.pem          /lrsql/config/server.crt.pem
ADD my_cacert.pem              /lrsql/config/cacert.pem

# custom authority
ADD my_authority.json.template /lrsql/config/authority.json.template

EXPOSE 8080
EXPOSE 8443
CMD ["/lrsql/bin/run_postgres.sh"]
```

The resulting image will use the provided configuration file and run Postgres. See [Getting Started](startup.md) for more configuration information.

[<- Back to Index](index.md)
