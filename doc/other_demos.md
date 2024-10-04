[<- Back to Index](index.md)

# Additional Deployment Configuration Examples

## Load Balanced LRS Demo

This demo illustrates how SQL LRS can be configured with multiple load balanced application servers with a single PostgreSQL database. The important configuration variable to pay attention to for multiple nodes in a single cluster is `LRSQL_JWT_COMMON_SECRET`, which allows the servers to share JWTs. Alternatively you may be able to implement session-sticky server rotation at the load balancer level, depending on your load balancer.

### Run the Docker Stack

    cd dev-resources/load_balanced
    docker compose up

## Proxied LRS Demo

This demo illustrates how SQL LRS must be configured if you are using a proxy (like nginx) to serve SQL LRS on a custom path. This is useful, for instance, for when you cannot use a dedicated domain/subdomain and need to serve SQL LRS from a path like `https://www.yetanalytics.com/my-lrs/...`. The important configuration variable to note for this situation is `LRSQL_PROXY_PATH` which tells the frontend to look for the server at that path. *NOTE: This variable does not actually move the location of SQL LRS endpoints, that must be done with a proxy, instead it just makes the components aware that that is happening*.

### Run the Docker Stack

    cd dev-resources/proxied_example
    docker compose up

## TLA Demo

This demo illustrates a configuration similar to the Total Learning Architecture, wherein multiple Noisy LRS instances are feeding a single Transactional LRS. In this demo, three PostgreSQL-backed LRSs will be launched, three LRS Pipe processes will consume their data, and a Transactional LRS will receive the aggregation of that data.

### Run the Docker Stack

    cd dev-resources/tla-demo
    docker compose up

[<- Back to Index](index.md)
