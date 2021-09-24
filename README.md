![SQL LRS Logo](doc/images/doc_logo.png)

# Yet Analytics SQL LRS

[![CI](https://github.com/yetanalytics/lrsql/actions/workflows/test.yml/badge.svg)](https://github.com/yetanalytics/lrsql/actions/workflows/test.yml)
[![CD](https://github.com/yetanalytics/lrsql/actions/workflows/build.yml/badge.svg)](https://github.com/yetanalytics/lrsql/actions/workflows/build.yml)

A SQL-based Learning Record Store.

## What is SQL LRS?

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in Part 3 of the [xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md). SQL LRS is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like H2, SQLite, and Postgres.

## Releases 

For releases and release notes, see the [Releases](https://github.com/yetanalytics/lrsql/releases) page.

## Documentation

### Overview

- [SQL LRS Overview](doc/overview.md)

### Basic Configuration

- [Getting Started](doc/startup.md)
- [Setting up TLS/HTTP](doc/https.md)
- [Authority Configuration](doc/authority.md)

### DBMS-specific Sections

- [Postgres](doc/postgres.md)
- [SQLite](doc/sqlite.md)

### Reference

- [Configuration Variables](doc/env_vars.md)
- [HTTP Endpoints](doc/endpoints.md)
- [Developer Documentation](doc/dev.md)

## License

Copyright © 2021 Yet Analytics, Inc.

Distributed under the Apache License version 2.0.
