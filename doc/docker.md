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
    yetanalytics/lrsql:latest
```

After SQL LRS starts and you see the logo, navigate to [http://0.0.0.0:8080/admin](http://0.0.0.0:8080/admin) to access the UI.

Note that the `-it` option will give you a pseudo-TTY and attach you to the container, allowing you to stop the SQL LRS container with ^C. It is not needed for production use, where `-d` would be preferable. See the [docker run docs](https://docs.docker.com/engine/reference/commandline/run/) for more information.

See [Configuration Variables](env_vars.md) for more options.

#### Other DBMSs

The SQL LRS Dockerfile uses the SQLite run script in `bin` as the default `CMD`. To change to another DBMS use the corresponding script. For instance to use PostgreSQL:

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

Note that you will also need to ensure that a PostgreSQL instance is accessible to the container for this command to work. For a demonstration of containerized SQL LRS with PostgreSQL you can use the `docker-compose.yml` file in the project root:

``` shell
docker compose up
```

Docker will start PostgreSQL and then SQL LRS. Note that PostgreSQL can sometimes take a while to start causing SQL LRS initialization to fail. If this happens, stop the system with ctrl-C and run the command again.

### Customization

The SQL LRS image can be used as a base image for a customized docker image.

For instance, to make an image with a custom configuration:

``` dockerfile
FROM yetanalytics/lrsql:latest
ADD my_lrsql.json /lrsql/config/lrsql.json
EXPOSE 8080
EXPOSE 8443
CMD ["/lrsql/bin/run_postgres.sh"]
```

The resulting image will use the provided configuration file and run PostgreSQL. See [Getting Started](startup.md) for more configuration information.

[<- Back to Index](index.md)
