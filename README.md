![SQL LRS Logo](doc/images/doc_logo.png)

# Yet Analytics SQL LRS

[![CI](https://github.com/yetanalytics/lrsql/actions/workflows/test.yml/badge.svg)](https://github.com/yetanalytics/lrsql/actions/workflows/test.yml)
[![CD](https://github.com/yetanalytics/lrsql/actions/workflows/build.yml/badge.svg)](https://github.com/yetanalytics/lrsql/actions/workflows/build.yml)
[![Docker Image Version (latest semver)](https://img.shields.io/docker/v/yetanalytics/lrsql?label=docker&style=plastic&color=blue)](https://hub.docker.com/r/yetanalytics/lrsql)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.1-5e0b73.svg)](CODE_OF_CONDUCT.md)

A SQL-based Learning Record Store.

## What is SQL LRS?

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in Part 3 of the [xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md). SQL LRS is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like SQLite and Postgres.

## Releases

For releases and release notes, see the [Releases](https://github.com/yetanalytics/lrsql/releases) page.

## Documentation

<!-- When you are updating this section, don't forget to also update doc/index.md -->

[SQL LRS Overview](doc/overview.md)

### FAQ

- [General Questions](doc/general_faq.md)
- [Troubleshooting](doc/troubleshooting.md)

### Basic Configuration

- [Getting Started](doc/startup.md)
- [Setting up TLS/HTTPS](doc/https.md)
- [Authority Configuration](doc/authority.md)
- [Docker Image](doc/docker.md)
- [OpenID Connect Support](doc/oidc.md)
  - [Auth0 Setup Guide](doc/oidc/auth0.md)

### DBMS-specific Sections

- [Postgres](doc/postgres.md)
- [SQLite](doc/sqlite.md)

### Reference

- [Configuration Variables](doc/env_vars.md)
- [HTTP Endpoints](doc/endpoints.md)
- [Developer Documentation](doc/dev.md)
- [Example AWS Deployment](doc/aws.md)
- [Reactions](doc/reactions.md)
- [Sending xAPI statement(s) with Postman](doc/postman.md)

### Demos

- [Visualization with Apache Superset](doc/superset.md)

## Contribution

Before contributing to this project, please read the [Contribution Guidelines](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Copyright © 2021-2024 Yet Analytics, Inc.

Distributed under the Apache License version 2.0.
